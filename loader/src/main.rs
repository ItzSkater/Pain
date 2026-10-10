//! Лоадер Pain Client.
//!
//! Входит в аккаунт на сервере Pain, показывает доступную версию (Free или Alpha),
//! скачивает сборку, сверяет контрольную сумму и кладёт её в папку `mods`.
//! Токен входа и выбранная папка запоминаются в файле настроек.

use std::fs;
use std::io::{self, Read, Write};
use std::path::{Path, PathBuf};
use std::time::Duration;

use serde_json::{json, Value};
use sha2::{Digest, Sha256};

/// Адрес сервера по умолчанию. Переопределяется переменной окружения PAIN_SERVER.
const DEFAULT_SERVER: &str = "http://de-bots4.h1cloud.net:25065";
const USER_AGENT: &str = concat!("PainLoader/", env!("CARGO_PKG_VERSION"));

struct Config {
    path: PathBuf,
    server: String,
    token: Option<String>,
    mods_dir: Option<PathBuf>,
}

impl Config {
    fn load() -> Config {
        let path = config_dir().join("config.json");
        let saved: Value = fs::read_to_string(&path)
            .ok()
            .and_then(|text| serde_json::from_str(&text).ok())
            .unwrap_or(Value::Null);
        let server = std::env::var("PAIN_SERVER")
            .ok()
            .or_else(|| saved["server"].as_str().map(str::to_owned))
            .unwrap_or_else(|| DEFAULT_SERVER.to_owned());
        Config {
            path,
            server: server.trim_end_matches('/').to_owned(),
            token: saved["token"].as_str().map(str::to_owned),
            mods_dir: saved["mods_dir"].as_str().map(PathBuf::from),
        }
    }

    fn save(&self) {
        let data = json!({
            "server": self.server,
            "token": self.token,
            "mods_dir": self.mods_dir.as_ref().map(|p| p.to_string_lossy().into_owned()),
        });
        if let Some(dir) = self.path.parent() {
            let _ = fs::create_dir_all(dir);
        }
        if fs::write(&self.path, serde_json::to_string_pretty(&data).unwrap_or_default()).is_ok() {
            restrict(&self.path);
        }
    }
}

/// Файл с токеном должен читать только владелец.
#[cfg(unix)]
fn restrict(path: &Path) {
    use std::os::unix::fs::PermissionsExt;
    let _ = fs::set_permissions(path, fs::Permissions::from_mode(0o600));
}

#[cfg(not(unix))]
fn restrict(_path: &Path) {}

fn home() -> PathBuf {
    std::env::var_os("HOME")
        .or_else(|| std::env::var_os("USERPROFILE"))
        .map(PathBuf::from)
        .unwrap_or_else(|| PathBuf::from("."))
}

fn config_dir() -> PathBuf {
    if let Some(dir) = std::env::var_os("APPDATA") {
        return PathBuf::from(dir).join("PainLoader");
    }
    if let Some(dir) = std::env::var_os("XDG_CONFIG_HOME") {
        return PathBuf::from(dir).join("pain-loader");
    }
    home().join(".config").join("pain-loader")
}

struct User {
    username: String,
    role: String,
    alpha: bool,
}

impl User {
    fn from(value: &Value) -> Option<User> {
        Some(User {
            username: value["username"].as_str()?.to_owned(),
            role: value["role"].as_str().unwrap_or("free").to_owned(),
            alpha: value["alpha"].as_bool().unwrap_or(false),
        })
    }

    fn label(&self) -> &str {
        match self.role.as_str() {
            "admin" => "Admin",
            _ if self.alpha => "Alpha",
            _ => "Free",
        }
    }
}

struct Api<'a> {
    config: &'a Config,
    agent: ureq::Agent,
}

impl<'a> Api<'a> {
    fn new(config: &'a Config) -> Api<'a> {
        let agent = ureq::AgentBuilder::new()
            .timeout_connect(Duration::from_secs(15))
            .timeout_read(Duration::from_secs(60))
            .user_agent(USER_AGENT)
            .build();
        Api { config, agent }
    }

    fn url(&self, path: &str) -> String {
        format!("{}{}", self.config.server, path)
    }

    fn with_auth(&self, request: ureq::Request) -> ureq::Request {
        match &self.config.token {
            Some(token) => request.set("Authorization", &format!("Bearer {token}")),
            None => request,
        }
    }

    /// Ответ сервера или понятный текст ошибки.
    fn finish(result: Result<ureq::Response, ureq::Error>) -> Result<Value, String> {
        match result {
            Ok(response) => response.into_json::<Value>().map_err(|e| format!("Сервер ответил непонятно: {e}")),
            Err(ureq::Error::Status(code, response)) => {
                let message = response
                    .into_json::<Value>()
                    .ok()
                    .and_then(|v| v["error"].as_str().map(str::to_owned))
                    .unwrap_or_else(|| format!("Ошибка сервера {code}"));
                Err(message)
            }
            Err(ureq::Error::Transport(e)) => Err(format!("Сервер не отвечает: {e}")),
        }
    }

    fn get(&self, path: &str) -> Result<Value, String> {
        Self::finish(self.with_auth(self.agent.get(&self.url(path))).call())
    }

    fn post(&self, path: &str, body: Value) -> Result<Value, String> {
        Self::finish(self.with_auth(self.agent.post(&self.url(path))).send_json(body))
    }
}

fn prompt(text: &str) -> String {
    print!("{text}");
    let _ = io::stdout().flush();
    let mut line = String::new();
    if io::stdin().read_line(&mut line).unwrap_or(0) == 0 {
        // ввод закрыт (Ctrl+D) — выходим, а не крутимся в цикле
        println!();
        std::process::exit(0);
    }
    line.trim().to_owned()
}

fn prompt_password(text: &str) -> String {
    // пароль не показывается на экране при вводе
    rpassword::prompt_password(text).unwrap_or_else(|_| prompt(""))
}

fn megabytes(bytes: u64) -> String {
    format!("{:.1} МБ", bytes as f64 / 1_048_576.0)
}

fn line() {
    println!("────────────────────────────────────────────");
}

/// Папки `mods`, которые удалось найти у известных лаунчеров.
fn find_mods_dirs() -> Vec<PathBuf> {
    let home = home();
    let mut found = Vec::new();
    let mut add = |path: PathBuf| {
        if path.is_dir() && !found.contains(&path) {
            found.push(path);
        }
    };
    let mut minecraft_roots = vec![home.join(".minecraft")];
    if let Some(appdata) = std::env::var_os("APPDATA") {
        minecraft_roots.push(PathBuf::from(appdata).join(".minecraft"));
    }
    minecraft_roots.push(home.join("Library/Application Support/minecraft"));
    for root in minecraft_roots {
        add(root.join("mods"));
    }
    // лаунчеры, где у каждой сборки своя папка
    let mut instance_roots = vec![
        (home.join(".minecraftx/instances"), "mods"),
        (home.join(".local/share/PrismLauncher/instances"), ".minecraft/mods"),
        (home.join(".local/share/PrismLauncher/instances"), "minecraft/mods"),
        (home.join(".var/app/org.prismlauncher.PrismLauncher/data/PrismLauncher/instances"), ".minecraft/mods"),
        (home.join(".local/share/multimc/instances"), ".minecraft/mods"),
    ];
    if let Some(appdata) = std::env::var_os("APPDATA") {
        let appdata = PathBuf::from(appdata);
        instance_roots.push((appdata.join("PrismLauncher/instances"), ".minecraft/mods"));
        instance_roots.push((appdata.join(".minecraftx/instances"), "mods"));
    }
    for (root, tail) in instance_roots {
        if let Ok(entries) = fs::read_dir(&root) {
            let mut dirs: Vec<PathBuf> = entries.flatten().map(|e| e.path()).filter(|p| p.is_dir()).collect();
            dirs.sort();
            for dir in dirs {
                add(dir.join(tail));
            }
        }
    }
    found
}

fn choose_mods_dir(config: &mut Config) {
    line();
    let found = find_mods_dirs();
    if found.is_empty() {
        println!("Папки mods не нашлись автоматически.");
    } else {
        println!("Найденные папки mods:");
        for (i, dir) in found.iter().enumerate() {
            println!("  {}) {}", i + 1, dir.display());
        }
    }
    let answer = prompt("Номер из списка или полный путь к папке mods (пусто — отмена): ");
    if answer.is_empty() {
        return;
    }
    let chosen = match answer.parse::<usize>() {
        Ok(n) if n >= 1 && n <= found.len() => found[n - 1].clone(),
        _ => {
            // ~ в начале пути разворачиваем сами: оболочка здесь этого не делает
            let expanded = match answer.strip_prefix("~/") {
                Some(rest) => home().join(rest),
                None => PathBuf::from(&answer),
            };
            if !expanded.is_dir() {
                println!("Такой папки нет: {}", expanded.display());
                return;
            }
            expanded
        }
    };
    println!("Папка выбрана: {}", chosen.display());
    config.mods_dir = Some(chosen);
    config.save();
}

/// Старые файлы клиента в папке mods — их убираем, чтобы мод не загрузился дважды.
fn is_pain_jar(name: &str) -> bool {
    let lower = name.to_lowercase();
    lower.ends_with(".jar") && (lower.starts_with("pain.v") || lower.starts_with("pain-free") || lower.starts_with("pain-alpha"))
}

fn install(config: &Config, edition: &str) -> Result<(), String> {
    let mods = config.mods_dir.clone().ok_or("Сначала выбери папку mods")?;
    let api = Api::new(config);
    let builds = api.get("/api/builds")?;
    let build = &builds["builds"][edition];
    let expected = build["sha256"].as_str().ok_or("Эта версия ещё не выложена на сервер")?.to_owned();
    let size = build["size"].as_u64().unwrap_or(0);
    let version = build["version"].as_str().unwrap_or("?").to_owned();
    let title = if edition == "alpha" { "Alpha" } else { "Free" };
    println!("Скачиваю Pain {title} {version} ({})…", megabytes(size));

    let response = match api.with_auth(api.agent.get(&api.url(&format!("/api/download/{edition}")))).call() {
        Ok(response) => response,
        Err(error) => return Api::finish(Err(error)).map(|_| ()),
    };
    let temp = mods.join(format!(".pain-{edition}.part"));
    let mut file = fs::File::create(&temp).map_err(|e| format!("Не могу писать в папку mods: {e}"))?;
    let mut reader = response.into_reader();
    let mut hasher = Sha256::new();
    let mut buffer = vec![0u8; 64 * 1024];
    let mut done: u64 = 0;
    let mut shown = 0;
    loop {
        let read = reader.read(&mut buffer).map_err(|e| {
            let _ = fs::remove_file(&temp);
            format!("Скачивание оборвалось: {e}")
        })?;
        if read == 0 {
            break;
        }
        file.write_all(&buffer[..read]).map_err(|e| format!("Не удалось записать файл: {e}"))?;
        hasher.update(&buffer[..read]);
        done += read as u64;
        if size > 0 {
            let percent = done * 100 / size;
            if percent / 5 != shown {
                shown = percent / 5;
                print!("\r  {percent:>3}%  {}", megabytes(done));
                let _ = io::stdout().flush();
            }
        }
    }
    println!();
    drop(file);

    // файл с чужой суммой в mods не попадает: это либо обрыв, либо подмена по дороге
    let actual = format!("{:x}", hasher.finalize());
    if actual != expected {
        let _ = fs::remove_file(&temp);
        return Err("Контрольная сумма не совпала — файл повреждён при скачивании. Попробуй ещё раз.".to_owned());
    }

    if let Ok(entries) = fs::read_dir(&mods) {
        for entry in entries.flatten() {
            let name = entry.file_name().to_string_lossy().into_owned();
            if is_pain_jar(&name) {
                let _ = fs::remove_file(entry.path());
            }
        }
    }
    let target = mods.join(format!("Pain-{title}.jar"));
    fs::rename(&temp, &target).map_err(|e| format!("Не удалось положить файл в mods: {e}"))?;
    println!("Готово: {}", target.display());

    let has_fabric_api = fs::read_dir(&mods)
        .map(|entries| entries.flatten().any(|e| e.file_name().to_string_lossy().to_lowercase().starts_with("fabric-api")))
        .unwrap_or(false);
    if !has_fabric_api {
        println!("В этой папке нет Fabric API — без него клиент не запустится. Поставь его через лаунчер.");
    }
    Ok(())
}

fn sign_in(config: &mut Config) -> Option<User> {
    loop {
        line();
        println!("  1) Войти");
        println!("  2) Создать аккаунт");
        println!("  0) Закрыть");
        let register = match prompt("> ").as_str() {
            "1" => false,
            "2" => true,
            "0" => return None,
            _ => continue,
        };
        let username = prompt("Ник: ");
        let password = prompt_password("Пароль: ");
        if register {
            if prompt_password("Пароль ещё раз: ") != password {
                println!("Пароли не совпадают.");
                continue;
            }
        }
        let path = if register { "/api/register" } else { "/api/login" };
        let result = Api::new(config).post(path, json!({ "username": username, "password": password }));
        match result {
            Ok(answer) => {
                config.token = answer["token"].as_str().map(str::to_owned);
                config.save();
                if let Some(user) = User::from(&answer["user"]) {
                    return Some(user);
                }
            }
            Err(message) => println!("{message}"),
        }
    }
}

fn main() {
    let mut config = Config::load();
    println!();
    println!("  Pain Loader {}", env!("CARGO_PKG_VERSION"));
    println!("  сервер: {}", config.server);
    if config.server.starts_with("http://") {
        println!("  соединение не зашифровано — не используй здесь пароль от других сайтов");
    }

    // сохранённый вход: проверяем, что токен ещё действует
    let mut user = match &config.token {
        Some(_) => match Api::new(&config).get("/api/me") {
            Ok(answer) => User::from(&answer["user"]),
            Err(message) => {
                if message.starts_with("Сервер не отвечает") {
                    println!("{message}");
                    return;
                }
                config.token = None;
                config.save();
                None
            }
        },
        None => None,
    };

    loop {
        let current = match user.take() {
            Some(current) => current,
            None => match sign_in(&mut config) {
                Some(current) => current,
                None => return,
            },
        };

        if config.mods_dir.as_ref().map(|p| !p.is_dir()).unwrap_or(true) {
            config.mods_dir = None;
            let found = find_mods_dirs();
            if found.len() == 1 {
                config.mods_dir = Some(found[0].clone());
                config.save();
            }
        }

        line();
        println!("  Аккаунт: {} · {}", current.username, current.label());
        match &config.mods_dir {
            Some(dir) => println!("  Папка mods: {}", dir.display()),
            None => println!("  Папка mods: не выбрана"),
        }
        match Api::new(&config).get("/api/builds") {
            Ok(builds) => {
                for (edition, title) in [("free", "Free"), ("alpha", "Alpha")] {
                    let build = &builds["builds"][edition];
                    match build["version"].as_str() {
                        Some(version) => println!("  Pain {title}: {version}, {}", megabytes(build["size"].as_u64().unwrap_or(0))),
                        None => println!("  Pain {title}: ещё не выложена"),
                    }
                }
            }
            Err(message) => println!("  {message}"),
        }
        line();
        println!("  1) Установить Pain Free");
        if current.alpha {
            println!("  2) Установить Pain Alpha");
        } else {
            println!("  2) Ввести ключ Alpha");
        }
        println!("  3) Выбрать папку mods");
        println!("  4) Выйти из аккаунта");
        println!("  0) Закрыть");

        let choice = prompt("> ");
        let mut next = Some(current);
        match choice.as_str() {
            "1" | "2" if choice == "1" || next.as_ref().map(|u| u.alpha).unwrap_or(false) => {
                if config.mods_dir.is_none() {
                    choose_mods_dir(&mut config);
                }
                if config.mods_dir.is_some() {
                    let edition = if choice == "1" { "free" } else { "alpha" };
                    if let Err(message) = install(&config, edition) {
                        println!("{message}");
                    }
                }
            }
            "2" => {
                let key = prompt("Ключ: ");
                if !key.is_empty() {
                    match Api::new(&config).post("/api/redeem", json!({ "key": key })) {
                        Ok(answer) => {
                            println!("Ключ принят, доступ к Alpha открыт.");
                            next = User::from(&answer["user"]).or(next);
                        }
                        Err(message) => println!("{message}"),
                    }
                }
            }
            "3" => choose_mods_dir(&mut config),
            "4" => {
                let _ = Api::new(&config).post("/api/logout", json!({}));
                config.token = None;
                config.save();
                next = None;
                println!("Вышел из аккаунта.");
            }
            "0" => return,
            _ => {}
        }
        user = next;
    }
}
