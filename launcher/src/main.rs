//! Лаунчер Pain Client: окно входа, выбор версии и кнопка «Играть».
//! Вся работа — в core.rs; здесь только окно и запуск фоновых задач.

mod core;

use std::sync::mpsc::{channel, Receiver};

use core::{Config, Event};
use eframe::egui::{self, Color32, RichText};
use serde_json::{json, Value};

#[derive(Clone)]
struct User {
    username: String,
    role: String,
    alpha: bool,
}

impl User {
    fn from(value: &Value) -> Option<User> {
        Some(User { username: value["username"].as_str()?.to_owned(), role: value["role"].as_str().unwrap_or("free").to_owned(), alpha: value["alpha"].as_bool().unwrap_or(false) })
    }

    fn label(&self) -> &str {
        match self.role.as_str() {
            "admin" => "Admin",
            _ if self.alpha => "Alpha",
            _ => "Free",
        }
    }
}

/// Что вернула фоновая задача входа или ключа.
enum Auth {
    Signed(Result<(Option<String>, User), String>),
    Redeemed(Result<User, String>),
}

struct App {
    config: Config,
    user: Option<User>,
    login: String,
    password: String,
    key: String,
    message: String,
    status: String,
    progress: Option<(u64, u64)>,
    log: Vec<String>,
    busy: bool,
    playing: bool,
    show_log: bool,
    events: Option<Receiver<Event>>,
    auth: Option<Receiver<Auth>>,
}

impl App {
    fn new(cc: &eframe::CreationContext<'_>) -> App {
        // строгая тёмная тема: чёрный фон, серые панели, белые акценты — без цветных градиентов
        let mut visuals = egui::Visuals::dark();
        visuals.panel_fill = Color32::from_rgb(10, 10, 10);
        visuals.window_fill = Color32::from_rgb(10, 10, 10);
        visuals.extreme_bg_color = Color32::from_rgb(18, 18, 18);
        visuals.selection.bg_fill = Color32::from_rgb(235, 235, 235);
        visuals.selection.stroke = egui::Stroke::new(1.0, Color32::from_rgb(10, 10, 10));
        visuals.widgets.inactive.bg_fill = Color32::from_rgb(28, 28, 28);
        visuals.widgets.inactive.weak_bg_fill = Color32::from_rgb(28, 28, 28);
        visuals.widgets.hovered.bg_fill = Color32::from_rgb(44, 44, 44);
        visuals.widgets.hovered.weak_bg_fill = Color32::from_rgb(44, 44, 44);
        visuals.widgets.active.bg_fill = Color32::from_rgb(60, 60, 60);
        visuals.widgets.active.weak_bg_fill = Color32::from_rgb(60, 60, 60);
        for widget in [&mut visuals.widgets.inactive, &mut visuals.widgets.hovered, &mut visuals.widgets.active, &mut visuals.widgets.noninteractive] {
            widget.rounding = egui::Rounding::same(8.0);
        }
        cc.egui_ctx.set_visuals(visuals);
        let mut style = (*cc.egui_ctx.style()).clone();
        style.spacing.item_spacing = egui::vec2(10.0, 10.0);
        style.spacing.button_padding = egui::vec2(14.0, 8.0);
        style.spacing.interact_size.y = 34.0;
        cc.egui_ctx.set_style(style);

        let config = Config::load();
        let mut app = App {
            login: String::new(),
            password: String::new(),
            key: String::new(),
            message: String::new(),
            status: String::new(),
            progress: None,
            log: Vec::new(),
            busy: false,
            playing: false,
            show_log: false,
            events: None,
            auth: None,
            user: None,
            config,
        };
        // сохранённый вход проверяем в фоне, чтобы окно открылось сразу
        if app.config.token.is_some() {
            app.busy = true;
            app.status = "Проверяю вход…".to_owned();
            let (tx, rx) = channel();
            let cfg = app.config.clone();
            std::thread::spawn(move || {
                let result = core::api_get(&cfg, "/api/me").and_then(|v| User::from(&v["user"]).ok_or_else(|| "Сервер не вернул аккаунт".to_owned()));
                let _ = tx.send(Auth::Signed(result.map(|user| (None, user))));
            });
            app.auth = Some(rx);
        }
        app
    }

    fn sign(&mut self, register: bool) {
        self.busy = true;
        self.message.clear();
        self.status = if register { "Создаю аккаунт…" } else { "Вхожу…" }.to_owned();
        let (tx, rx) = channel();
        let cfg = self.config.clone();
        let body = json!({ "username": self.login.trim(), "password": self.password });
        std::thread::spawn(move || {
            let result = core::api_post(&cfg, if register { "/api/register" } else { "/api/login" }, body).and_then(|v| {
                let user = User::from(&v["user"]).ok_or_else(|| "Сервер не вернул аккаунт".to_owned())?;
                Ok((v["token"].as_str().map(str::to_owned), user))
            });
            let _ = tx.send(Auth::Signed(result));
        });
        self.auth = Some(rx);
    }

    fn redeem(&mut self) {
        self.busy = true;
        self.message.clear();
        let (tx, rx) = channel();
        let cfg = self.config.clone();
        let key = self.key.trim().to_owned();
        std::thread::spawn(move || {
            let result = core::api_post(&cfg, "/api/redeem", json!({ "key": key })).and_then(|v| User::from(&v["user"]).ok_or_else(|| "Сервер не вернул аккаунт".to_owned()));
            let _ = tx.send(Auth::Redeemed(result));
        });
        self.auth = Some(rx);
    }

    fn play(&mut self) {
        self.busy = true;
        self.message.clear();
        self.log.clear();
        self.progress = None;
        self.status = "Готовлю игру…".to_owned();
        self.config.save();
        let (tx, rx) = channel();
        let cfg = self.config.clone();
        std::thread::spawn(move || {
            let result = core::prepare(&cfg, &tx).and_then(|installed| {
                let _ = tx.send(Event::Progress(0, 0));
                core::launch(&cfg, &installed, &tx)
            });
            let _ = tx.send(Event::Done(result));
        });
        self.events = Some(rx);
    }

    fn pump(&mut self) {
        if let Some(rx) = &self.auth {
            if let Ok(answer) = rx.try_recv() {
                self.auth = None;
                self.busy = false;
                self.status.clear();
                match answer {
                    Auth::Signed(Ok((token, user))) => {
                        if token.is_some() {
                            self.config.token = token;
                        }
                        if self.config.nick.is_empty() {
                            self.config.nick = user.username.clone();
                        }
                        if !user.alpha {
                            self.config.edition = "free".to_owned();
                        }
                        self.password.clear();
                        self.config.save();
                        self.user = Some(user);
                    }
                    Auth::Signed(Err(error)) => {
                        // сохранённый вход больше не действует — показываем форму входа
                        if self.user.is_none() && self.login.is_empty() && !error.contains("не отвечает") {
                            self.config.token = None;
                            self.config.save();
                        }
                        self.message = error;
                    }
                    Auth::Redeemed(Ok(user)) => {
                        self.key.clear();
                        self.message = "Ключ принят, доступ к Alpha открыт.".to_owned();
                        self.config.edition = "alpha".to_owned();
                        self.config.save();
                        self.user = Some(user);
                    }
                    Auth::Redeemed(Err(error)) => self.message = error,
                }
            }
        }
        let mut finished = None;
        if let Some(rx) = &self.events {
            while let Ok(event) = rx.try_recv() {
                match event {
                    Event::Status(text) => {
                        self.playing = text == "Игра запущена";
                        self.status = text;
                    }
                    Event::Progress(done, total) => self.progress = if total > 0 { Some((done, total)) } else { None },
                    Event::Log(line) => {
                        self.log.push(line);
                        if self.log.len() > 400 {
                            self.log.drain(..100);
                        }
                    }
                    Event::Done(result) => finished = Some(result),
                }
            }
        }
        if let Some(result) = finished {
            self.events = None;
            self.busy = false;
            self.playing = false;
            self.progress = None;
            match result {
                Ok(()) => self.status = "Игра закрыта".to_owned(),
                Err(error) => {
                    self.status.clear();
                    self.message = error;
                    self.show_log = !self.log.is_empty();
                }
            }
        }
    }
}

impl eframe::App for App {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.pump();
        if self.busy {
            ctx.request_repaint_after(std::time::Duration::from_millis(80));
        }
        let muted = Color32::from_rgb(150, 150, 150);
        egui::CentralPanel::default().frame(egui::Frame::default().fill(Color32::from_rgb(10, 10, 10)).inner_margin(28.0)).show(ctx, |ui| {
            ui.horizontal(|ui| {
                ui.label(RichText::new("Pain").size(30.0).strong().color(Color32::WHITE));
                ui.label(RichText::new("Launcher").size(30.0).color(muted));
            });
            ui.label(RichText::new(format!("Minecraft {} · Fabric", core::MC_VERSION)).color(muted));
            ui.add_space(14.0);

            let Some(user) = self.user.clone() else {
                // ---------- вход ----------
                let waiting = self.busy;
                ui.label("Ник");
                ui.add_enabled(!waiting, egui::TextEdit::singleline(&mut self.login).desired_width(f32::INFINITY));
                ui.label("Пароль");
                let field = ui.add_enabled(!waiting, egui::TextEdit::singleline(&mut self.password).password(true).desired_width(f32::INFINITY));
                let enter = field.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter));
                ui.add_space(4.0);
                let ready = !waiting && !self.login.trim().is_empty() && !self.password.is_empty();
                ui.horizontal(|ui| {
                    let login = ui.add_enabled(ready, egui::Button::new(RichText::new("Войти").strong().color(Color32::from_rgb(10, 10, 10))).fill(Color32::from_rgb(235, 235, 235)));
                    if login.clicked() || (enter && ready) {
                        self.sign(false);
                    }
                    if ui.add_enabled(ready, egui::Button::new("Создать аккаунт")).clicked() {
                        self.sign(true);
                    }
                });
                if !self.status.is_empty() {
                    ui.label(RichText::new(&self.status).color(muted));
                }
                if !self.message.is_empty() {
                    ui.label(RichText::new(&self.message).color(Color32::from_rgb(255, 130, 130)));
                }
                ui.add_space(8.0);
                ui.label(RichText::new("Аккаунт один для сайта и лаунчера. Соединение с сервером пока не зашифровано — не используй пароль от других сайтов.").color(muted).small());
                return;
            };

            // ---------- главный экран ----------
            let controls = !self.busy;
            ui.horizontal(|ui| {
                ui.label(RichText::new(&user.username).size(18.0).strong());
                ui.label(RichText::new(user.label()).color(muted));
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    if ui.add_enabled(controls, egui::Button::new("Выйти")).clicked() {
                        let cfg = self.config.clone();
                        std::thread::spawn(move || {
                            let _ = core::api_post(&cfg, "/api/logout", json!({}));
                        });
                        self.config.token = None;
                        self.config.save();
                        self.user = None;
                        self.message.clear();
                    }
                });
            });
            ui.separator();

            ui.label("Версия клиента");
            ui.horizontal(|ui| {
                ui.add_enabled_ui(controls, |ui| {
                    ui.selectable_value(&mut self.config.edition, "free".to_owned(), "  Pain Free  ");
                });
                ui.add_enabled_ui(controls && user.alpha, |ui| {
                    ui.selectable_value(&mut self.config.edition, "alpha".to_owned(), "  Pain Alpha  ");
                });
                if !user.alpha {
                    ui.label(RichText::new("Alpha открывается ключом").color(muted).small());
                }
            });

            if !user.alpha {
                ui.horizontal(|ui| {
                    ui.add_enabled(controls, egui::TextEdit::singleline(&mut self.key).hint_text("PAIN-XXXXX-XXXXX-XXXXX").desired_width(260.0));
                    if ui.add_enabled(controls && !self.key.trim().is_empty(), egui::Button::new("Активировать ключ")).clicked() {
                        self.redeem();
                    }
                });
            }

            ui.horizontal(|ui| {
                ui.label("Ник в игре");
                ui.add_enabled(controls, egui::TextEdit::singleline(&mut self.config.nick).char_limit(16).desired_width(180.0));
                ui.add_space(12.0);
                ui.label("Память");
                ui.add_enabled(controls, egui::Slider::new(&mut self.config.ram_gb, 2..=12).suffix(" ГБ"));
            });
            ui.add_space(6.0);

            let nick_ok = self.config.nick.len() >= 3 && self.config.nick.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
            let text = if self.playing { "Игра запущена" } else if self.busy { "Подготовка…" } else { "Играть" };
            let play = egui::Button::new(RichText::new(text).size(20.0).strong().color(Color32::from_rgb(10, 10, 10))).fill(Color32::from_rgb(235, 235, 235));
            // кнопка во всю ширину, надпись по центру
            let clicked = ui.add_enabled_ui(controls && nick_ok, |ui| ui.add_sized([ui.available_width(), 52.0], play).clicked()).inner;
            if clicked {
                self.play();
            }
            if !nick_ok {
                ui.label(RichText::new("Ник в игре: от 3 до 16 символов, латиница, цифры и _").color(muted).small());
            }

            if let Some((done, total)) = self.progress {
                ui.add(egui::ProgressBar::new(done as f32 / total as f32).desired_height(8.0).fill(Color32::from_rgb(235, 235, 235)));
            }
            if !self.status.is_empty() {
                ui.label(RichText::new(&self.status).color(muted));
            }
            if !self.message.is_empty() {
                let good = self.message.starts_with("Ключ принят");
                ui.label(RichText::new(&self.message).color(if good { Color32::from_rgb(210, 210, 210) } else { Color32::from_rgb(255, 130, 130) }));
            }

            if !self.log.is_empty() {
                ui.checkbox(&mut self.show_log, "Показать журнал игры");
                if self.show_log {
                    egui::ScrollArea::vertical().stick_to_bottom(true).auto_shrink([false, false]).show(ui, |ui| {
                        for line in &self.log {
                            ui.label(RichText::new(line).monospace().small().color(muted));
                        }
                    });
                }
            }
        });
    }
}

/// Режим без окна: `--prepare` только ставит всё нужное, `--play` ещё и запускает игру.
fn headless(play: bool) {
    let config = Config::load();
    let (tx, rx) = channel();
    let cfg = config.clone();
    let worker = std::thread::spawn(move || {
        let result = core::prepare(&cfg, &tx).and_then(|installed| {
            println!("\nустановлено: {} файлов в пути запуска, Fabric {}", installed.classpath.len(), installed.fabric_version);
            if play {
                core::launch(&cfg, &installed, &tx)
            } else {
                Ok(())
            }
        });
        let _ = tx.send(Event::Done(result));
    });
    let mut last = String::new();
    for event in rx {
        match event {
            Event::Status(text) => {
                // счётчики «N из M» не печатаем построчно — только смену этапа
                let stage = text.split(':').next().unwrap_or("").to_owned();
                if stage != last {
                    println!("{text}");
                    last = stage;
                }
            }
            Event::Log(line) => println!("  | {line}"),
            Event::Progress(..) => {}
            Event::Done(result) => {
                match result {
                    Ok(()) => println!("готово"),
                    Err(error) => {
                        println!("ошибка: {error}");
                        std::process::exit(1);
                    }
                }
                break;
            }
        }
    }
    let _ = worker.join();
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.iter().any(|a| a == "--prepare") {
        return headless(false);
    }
    if args.iter().any(|a| a == "--play") {
        return headless(true);
    }
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default().with_inner_size([560.0, 620.0]).with_min_inner_size([480.0, 520.0]).with_title("Pain Launcher").with_app_id("pain-launcher"),
        ..Default::default()
    };
    if let Err(error) = eframe::run_native("Pain Launcher", options, Box::new(|cc| Ok(Box::new(App::new(cc))))) {
        eprintln!("Не удалось открыть окно: {error}");
    }
}
