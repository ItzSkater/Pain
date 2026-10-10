//! Вся работа лаунчера без окна: аккаунт Pain, установка Minecraft + Fabric + клиента, запуск игры.

use std::collections::HashSet;
use std::fs;
use std::io::{BufRead, BufReader, Read, Write};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::mpsc::Sender;
use std::sync::{Arc, Mutex};
use std::time::Duration;

use serde_json::{json, Value};
use sha1::Sha1;
use sha2::{Digest, Sha256, Sha512};

pub const MC_VERSION: &str = "1.21.4";
/// Адрес сервера аккаунтов по умолчанию; переопределяется переменной PAIN_SERVER.
pub const DEFAULT_SERVER: &str = "http://45.133.151.170:25065";
const USER_AGENT: &str = concat!("PainLauncher/", env!("CARGO_PKG_VERSION"));
const FABRIC_API_URL: &str = "https://cdn.modrinth.com/data/P7dR8mSH/versions/p96k10UR/fabric-api-0.119.4%2B1.21.4.jar";
const FABRIC_API_FILE: &str = "fabric-api-0.119.4+1.21.4.jar";
const FABRIC_API_SHA512: &str = include_str!("fabric_api.sha512");

/// Что лаунчер сообщает окну (или консоли) по ходу работы.
pub enum Event {
    Status(String),
    Progress(u64, u64),
    Log(String),
    Done(Result<(), String>),
}

pub type Tx = Sender<Event>;

#[derive(Clone)]
pub struct Config {
    pub root: PathBuf,
    pub server: String,
    pub token: Option<String>,
    pub nick: String,
    pub ram_gb: u32,
    pub edition: String,
}

fn home() -> PathBuf {
    std::env::var_os("HOME").or_else(|| std::env::var_os("USERPROFILE")).map(PathBuf::from).unwrap_or_else(|| PathBuf::from("."))
}

fn data_root() -> PathBuf {
    if let Some(dir) = std::env::var_os("PAIN_LAUNCHER_DIR") {
        return PathBuf::from(dir);
    }
    if let Some(dir) = std::env::var_os("APPDATA") {
        return PathBuf::from(dir).join("PainLauncher");
    }
    if let Some(dir) = std::env::var_os("XDG_DATA_HOME") {
        return PathBuf::from(dir).join("pain-launcher");
    }
    home().join(".local/share/pain-launcher")
}

impl Config {
    pub fn load() -> Config {
        let root = data_root();
        let saved: Value = fs::read_to_string(root.join("launcher.json")).ok().and_then(|t| serde_json::from_str(&t).ok()).unwrap_or(Value::Null);
        let server = std::env::var("PAIN_SERVER").ok().or_else(|| saved["server"].as_str().map(str::to_owned)).unwrap_or_else(|| DEFAULT_SERVER.to_owned());
        Config {
            root,
            server: server.trim_end_matches('/').to_owned(),
            token: saved["token"].as_str().map(str::to_owned),
            nick: saved["nick"].as_str().unwrap_or("").to_owned(),
            ram_gb: saved["ram_gb"].as_u64().unwrap_or(4).clamp(2, 16) as u32,
            edition: saved["edition"].as_str().unwrap_or("free").to_owned(),
        }
    }

    pub fn save(&self) {
        let _ = fs::create_dir_all(&self.root);
        let path = self.root.join("launcher.json");
        // адрес сервера из PAIN_SERVER в файл не пишем: это временная подмена для проверки
        let data = json!({ "token": self.token, "nick": self.nick, "ram_gb": self.ram_gb, "edition": self.edition });
        if fs::write(&path, serde_json::to_string_pretty(&data).unwrap_or_default()).is_ok() {
            #[cfg(unix)]
            {
                use std::os::unix::fs::PermissionsExt;
                let _ = fs::set_permissions(&path, fs::Permissions::from_mode(0o600));
            }
        }
    }

    pub fn game_dir(&self) -> PathBuf {
        self.root.join("game")
    }
}

fn agent() -> ureq::Agent {
    ureq::AgentBuilder::new().timeout_connect(Duration::from_secs(20)).timeout_read(Duration::from_secs(90)).user_agent(USER_AGENT).build()
}

// ---------- аккаунт Pain ----------

fn finish(result: Result<ureq::Response, ureq::Error>) -> Result<Value, String> {
    match result {
        Ok(response) => response.into_json::<Value>().map_err(|_| "Сервер аккаунтов ответил непонятно — похоже, на нём запущена старая версия".to_owned()),
        Err(ureq::Error::Status(code, response)) => Err(response
            .into_json::<Value>()
            .ok()
            .and_then(|v| v["error"].as_str().map(str::to_owned))
            .unwrap_or_else(|| format!("Сервер аккаунтов ответил ошибкой {code} — похоже, на нём запущена старая версия"))),
        Err(ureq::Error::Transport(e)) => Err(format!("Сервер аккаунтов не отвечает: {e}")),
    }
}

fn with_auth(cfg: &Config, request: ureq::Request) -> ureq::Request {
    match &cfg.token {
        Some(token) => request.set("Authorization", &format!("Bearer {token}")),
        None => request,
    }
}

pub fn api_get(cfg: &Config, path: &str) -> Result<Value, String> {
    finish(with_auth(cfg, agent().get(&format!("{}{}", cfg.server, path))).call())
}

pub fn api_post(cfg: &Config, path: &str, body: Value) -> Result<Value, String> {
    finish(with_auth(cfg, agent().post(&format!("{}{}", cfg.server, path))).send_json(body))
}

// ---------- файлы ----------

fn hex<D: Digest>(mut hasher: D, path: &Path) -> Option<String> {
    let mut file = fs::File::open(path).ok()?;
    let mut buffer = vec![0u8; 1 << 16];
    loop {
        let read = file.read(&mut buffer).ok()?;
        if read == 0 {
            break;
        }
        hasher.update(&buffer[..read]);
    }
    Some(hasher.finalize().iter().map(|b| format!("{b:02x}")).collect())
}

fn sha1_ok(path: &Path, expected: &str) -> bool {
    hex(Sha1::new(), path).map(|h| h == expected).unwrap_or(false)
}

/// Папки других лаунчеров: если нужный файл уже скачан ими, берём его оттуда, а не из сети.
fn foreign_roots() -> Vec<PathBuf> {
    let home = home();
    let mut roots = vec![home.join(".minecraftx"), home.join(".minecraft"), home.join(".local/share/PrismLauncher")];
    if let Some(appdata) = std::env::var_os("APPDATA") {
        roots.push(PathBuf::from(appdata).join(".minecraft"));
    }
    roots.into_iter().filter(|p| p.is_dir()).collect()
}

fn copy_from_cache(rel: &str, dest: &Path, sha1: Option<&str>, roots: &[PathBuf]) -> bool {
    for root in roots {
        let candidate = root.join(rel);
        if candidate.is_file() && sha1.map(|s| sha1_ok(&candidate, s)).unwrap_or(true) {
            if let Some(parent) = dest.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if fs::hard_link(&candidate, dest).is_ok() || fs::copy(&candidate, dest).is_ok() {
                return true;
            }
        }
    }
    false
}

fn download(url: &str, dest: &Path, sha1: Option<&str>) -> Result<(), String> {
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent).map_err(|e| format!("Не могу создать папку {}: {e}", parent.display()))?;
    }
    let temp = dest.with_extension("part");
    let mut last = String::new();
    for attempt in 0..4 {
        if attempt > 0 {
            std::thread::sleep(Duration::from_secs(2));
        }
        let result = (|| -> Result<(), String> {
            let response = agent().get(url).call().map_err(|e| e.to_string())?;
            let mut file = fs::File::create(&temp).map_err(|e| e.to_string())?;
            std::io::copy(&mut response.into_reader(), &mut file).map_err(|e| e.to_string())?;
            file.flush().map_err(|e| e.to_string())?;
            if let Some(expected) = sha1 {
                if !sha1_ok(&temp, expected) {
                    return Err("контрольная сумма не совпала".to_owned());
                }
            }
            fs::rename(&temp, dest).map_err(|e| e.to_string())
        })();
        match result {
            Ok(()) => return Ok(()),
            Err(e) => last = e,
        }
    }
    let _ = fs::remove_file(&temp);
    Err(format!("Не удалось скачать {url}: {last}"))
}

/// Файл на месте и цел — ничего не делаем; иначе берём у соседнего лаунчера или качаем.
fn ensure(rel: &str, base: &Path, url: &str, sha1: Option<&str>, roots: &[PathBuf]) -> Result<PathBuf, String> {
    let dest = base.join(rel);
    if dest.is_file() && sha1.map(|s| sha1_ok(&dest, s)).unwrap_or(true) {
        return Ok(dest);
    }
    let _ = fs::remove_file(&dest);
    if copy_from_cache(rel, &dest, sha1, roots) {
        return Ok(dest);
    }
    download(url, &dest, sha1)?;
    Ok(dest)
}

fn fetch_json(url: &str) -> Result<Value, String> {
    agent().get(url).call().map_err(|e| format!("Нет связи с {url}: {e}"))?.into_json::<Value>().map_err(|e| format!("Непонятный ответ от {url}: {e}"))
}

fn os_name() -> &'static str {
    if cfg!(windows) {
        "windows"
    } else if cfg!(target_os = "macos") {
        "osx"
    } else {
        "linux"
    }
}

/// Правила Mojang: библиотека нужна, если последнее подходящее правило разрешает её для нашей системы.
fn allowed(library: &Value) -> bool {
    let rules = match library["rules"].as_array() {
        Some(rules) => rules,
        None => return true,
    };
    let mut allow = false;
    for rule in rules {
        let matches = rule["os"]["name"].as_str().map(|name| name == os_name()).unwrap_or(true);
        if matches {
            allow = rule["action"].as_str() == Some("allow");
        }
    }
    allow
}

/// «группа:имя:версия[:классификатор]» → путь в хранилище Maven.
fn maven_path(name: &str) -> Option<String> {
    let parts: Vec<&str> = name.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let classifier = parts.get(3).map(|c| format!("-{c}")).unwrap_or_default();
    Some(format!("{}/{}/{}/{}-{}{}.jar", parts[0].replace('.', "/"), parts[1], parts[2], parts[1], parts[2], classifier))
}

/// Что нужно для запуска после установки.
pub struct Installed {
    pub classpath: Vec<PathBuf>,
    pub main_class: String,
    pub asset_index: String,
    pub jvm_args: Vec<String>,
    pub fabric_version: String,
}

pub fn prepare(cfg: &Config, tx: &Tx) -> Result<Installed, String> {
    let roots = foreign_roots();
    let assets_dir = cfg.root.join("assets");
    let version_dir = cfg.root.join("versions").join(MC_VERSION);
    let status = |text: &str| {
        let _ = tx.send(Event::Status(text.to_owned()));
    };

    // --- описание версии Minecraft ---
    status("Проверяю Minecraft…");
    let version_file = version_dir.join(format!("{MC_VERSION}.json"));
    let version: Value = match fs::read_to_string(&version_file).ok().and_then(|t| serde_json::from_str(&t).ok()) {
        Some(value) => value,
        None => {
            // у соседнего лаунчера описание версии уже может лежать — тогда сеть не нужна
            let cached = roots.iter().map(|r| r.join(format!("versions/{MC_VERSION}/{MC_VERSION}.json"))).find_map(|p| fs::read_to_string(p).ok()).and_then(|t| serde_json::from_str::<Value>(&t).ok());
            let value = match cached {
                Some(value) => value,
                None => {
                    let manifest = fetch_json("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")?;
                    let url = manifest["versions"]
                        .as_array()
                        .and_then(|list| list.iter().find(|v| v["id"].as_str() == Some(MC_VERSION)))
                        .and_then(|v| v["url"].as_str())
                        .ok_or("В списке версий Mojang нет нужной версии")?
                        .to_owned();
                    fetch_json(&url)?
                }
            };
            fs::create_dir_all(&version_dir).map_err(|e| e.to_string())?;
            fs::write(&version_file, value.to_string()).map_err(|e| e.to_string())?;
            value
        }
    };

    // --- сама игра ---
    status("Проверяю файл игры…");
    let client = &version["downloads"]["client"];
    let client_jar = ensure(
        &format!("versions/{MC_VERSION}/{MC_VERSION}.jar"),
        &cfg.root,
        client["url"].as_str().ok_or("В описании версии нет ссылки на игру")?,
        client["sha1"].as_str(),
        &roots,
    )?;

    // --- библиотеки игры ---
    let mut classpath: Vec<PathBuf> = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let libraries: Vec<&Value> = version["libraries"].as_array().map(|l| l.iter().filter(|lib| allowed(lib)).collect()).unwrap_or_default();
    let total = libraries.len() as u64;
    for (i, library) in libraries.iter().enumerate() {
        let artifact = &library["downloads"]["artifact"];
        let (Some(path), Some(url)) = (artifact["path"].as_str(), artifact["url"].as_str()) else { continue };
        status(&format!("Библиотеки игры: {} из {}", i + 1, total));
        let _ = tx.send(Event::Progress(i as u64 + 1, total));
        let file = ensure(&format!("libraries/{path}"), &cfg.root, url, artifact["sha1"].as_str(), &roots)?;
        if seen.insert(path.to_owned()) {
            classpath.push(file);
        }
    }

    // --- Fabric ---
    status("Проверяю Fabric…");
    let profile_file = version_dir.join("fabric-profile.json");
    let profile: Value = match fs::read_to_string(&profile_file).ok().and_then(|t| serde_json::from_str(&t).ok()) {
        Some(value) => value,
        None => {
            let loaders = fetch_json(&format!("https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}"))?;
            let loader = loaders
                .as_array()
                .and_then(|list| list.iter().find(|l| l["loader"]["stable"].as_bool() == Some(true)).or_else(|| list.first()))
                .and_then(|l| l["loader"]["version"].as_str())
                .ok_or("Не нашлась версия Fabric для этой версии игры")?
                .to_owned();
            let value = fetch_json(&format!("https://meta.fabricmc.net/v2/versions/loader/{MC_VERSION}/{loader}/profile/json"))?;
            fs::write(&profile_file, value.to_string()).map_err(|e| e.to_string())?;
            value
        }
    };
    let fabric_libraries = profile["libraries"].as_array().cloned().unwrap_or_default();
    // библиотеки Fabric идут в начало: у него свои версии некоторых общих библиотек (например, ASM)
    let mut fabric_path: Vec<PathBuf> = Vec::new();
    let mut fabric_names: HashSet<String> = HashSet::new();
    for (i, library) in fabric_libraries.iter().enumerate() {
        let Some(name) = library["name"].as_str() else { continue };
        let Some(path) = maven_path(name) else { continue };
        status(&format!("Библиотеки Fabric: {} из {}", i + 1, fabric_libraries.len()));
        let base = library["url"].as_str().unwrap_or("https://maven.fabricmc.net/");
        let url = format!("{}{}", if base.ends_with('/') { base.to_owned() } else { format!("{base}/") }, path);
        fabric_path.push(ensure(&format!("libraries/{path}"), &cfg.root, &url, library["sha1"].as_str(), &roots)?);
        let mut parts = name.split(':');
        fabric_names.insert(format!("{}:{}", parts.next().unwrap_or(""), parts.next().unwrap_or("")));
    }
    // из библиотек игры убираем те, что Fabric принёс в своей версии
    classpath.retain(|file| {
        let text = file.to_string_lossy().replace('\\', "/");
        !fabric_names.iter().any(|ga| {
            let mut parts = ga.split(':');
            let group = parts.next().unwrap_or("").replace('.', "/");
            let artifact = parts.next().unwrap_or("");
            text.contains(&format!("libraries/{group}/{artifact}/"))
        })
    });
    let mut full = fabric_path;
    full.extend(classpath);
    full.push(client_jar);

    // --- ресурсы игры: звуки, языки, значки ---
    let index_id = version["assetIndex"]["id"].as_str().ok_or("В описании версии нет списка ресурсов")?.to_owned();
    status("Проверяю ресурсы игры…");
    let index_file = ensure(
        &format!("assets/indexes/{index_id}.json"),
        &cfg.root,
        version["assetIndex"]["url"].as_str().ok_or("В описании версии нет ссылки на список ресурсов")?,
        version["assetIndex"]["sha1"].as_str(),
        &roots,
    )?;
    let index: Value = serde_json::from_str(&fs::read_to_string(&index_file).map_err(|e| e.to_string())?).map_err(|e| e.to_string())?;
    let mut objects: Vec<(String, u64)> = index["objects"]
        .as_object()
        .map(|map| map.values().filter_map(|o| Some((o["hash"].as_str()?.to_owned(), o["size"].as_u64().unwrap_or(0)))).collect())
        .unwrap_or_default();
    objects.sort();
    objects.dedup();
    let objects = Arc::new(objects);
    let next = Arc::new(AtomicUsize::new(0));
    let done = Arc::new(AtomicUsize::new(0));
    let failure: Arc<Mutex<Option<String>>> = Arc::new(Mutex::new(None));
    let roots_shared = Arc::new(roots.clone());
    let mut workers = Vec::new();
    for _ in 0..8 {
        let (objects, next, done, failure, roots, assets_dir) = (objects.clone(), next.clone(), done.clone(), failure.clone(), roots_shared.clone(), assets_dir.clone());
        workers.push(std::thread::spawn(move || loop {
            let i = next.fetch_add(1, Ordering::SeqCst);
            if i >= objects.len() || failure.lock().map(|f| f.is_some()).unwrap_or(true) {
                break;
            }
            let (hash, size) = &objects[i];
            let rel = format!("objects/{}/{}", &hash[..2], hash);
            let dest = assets_dir.join(&rel);
            // ресурсов тысячи, поэтому целые проверяем по размеру, а не пересчитываем сумму каждого
            let present = fs::metadata(&dest).map(|m| m.len() == *size).unwrap_or(false);
            if !present {
                let _ = fs::remove_file(&dest);
                let cached = roots.iter().any(|root| {
                    let candidate = root.join("assets").join(&rel);
                    fs::metadata(&candidate).map(|m| m.len() == *size).unwrap_or(false) && {
                        let _ = dest.parent().map(fs::create_dir_all);
                        fs::hard_link(&candidate, &dest).is_ok() || fs::copy(&candidate, &dest).is_ok()
                    }
                });
                if !cached {
                    let url = format!("https://resources.download.minecraft.net/{}/{}", &hash[..2], hash);
                    if let Err(e) = download(&url, &dest, Some(hash)) {
                        if let Ok(mut slot) = failure.lock() {
                            slot.get_or_insert(e);
                        }
                        break;
                    }
                }
            }
            done.fetch_add(1, Ordering::SeqCst);
        }));
    }
    let total = objects.len() as u64;
    while workers.iter().any(|w| !w.is_finished()) {
        let n = done.load(Ordering::SeqCst) as u64;
        let _ = tx.send(Event::Status(format!("Ресурсы игры: {n} из {total}")));
        let _ = tx.send(Event::Progress(n, total));
        std::thread::sleep(Duration::from_millis(150));
    }
    for worker in workers {
        let _ = worker.join();
    }
    if let Some(error) = failure.lock().ok().and_then(|mut f| f.take()) {
        return Err(error);
    }

    // --- моды: Fabric API и сам Pain ---
    let mods = cfg.game_dir().join("mods");
    fs::create_dir_all(&mods).map_err(|e| e.to_string())?;
    let api = mods.join(FABRIC_API_FILE);
    let expected = FABRIC_API_SHA512.trim();
    if hex(Sha512::new(), &api).as_deref() != Some(expected) {
        status("Скачиваю Fabric API…");
        let cached = roots.iter().map(|r| r.join("instances")).filter_map(|d| fs::read_dir(d).ok()).flat_map(|d| d.flatten()).map(|e| e.path().join("mods").join(FABRIC_API_FILE)).find(|p| hex(Sha512::new(), p).as_deref() == Some(expected));
        match cached {
            Some(path) => {
                fs::copy(path, &api).map_err(|e| e.to_string())?;
            }
            None => {
                download(FABRIC_API_URL, &api, None)?;
                if hex(Sha512::new(), &api).as_deref() != Some(expected) {
                    let _ = fs::remove_file(&api);
                    return Err("Fabric API скачался повреждённым. Попробуй ещё раз.".to_owned());
                }
            }
        }
    }

    // для разработки: PAIN_DEV_JAR=путь — взять клиент из этого файла и не ходить на сервер аккаунтов
    let dev_jar = std::env::var_os("PAIN_DEV_JAR").map(PathBuf::from);
    if let Some(source) = &dev_jar {
        status("Беру клиент из PAIN_DEV_JAR…");
        fs::copy(source, mods.join("Pain.jar")).map_err(|e| format!("Не удалось взять {}: {e}", source.display()))?;
    }
    status("Проверяю клиент Pain…");
    let builds = if dev_jar.is_some() { json!({ "builds": { cfg.edition.as_str(): { "sha256": hex(Sha256::new(), &mods.join("Pain.jar")), "size": 0 } } }) } else { api_get(cfg, "/api/builds")? };
    let build = &builds["builds"][cfg.edition.as_str()];
    let wanted = build["sha256"].as_str().ok_or("Эта версия клиента ещё не выложена на сервер")?.to_owned();
    let size = build["size"].as_u64().unwrap_or(0);
    let pain = mods.join("Pain.jar");
    if hex(Sha256::new(), &pain).as_deref() != Some(wanted.as_str()) {
        status(&format!("Скачиваю Pain {}…", if cfg.edition == "alpha" { "Alpha" } else { "Free" }));
        let response = match with_auth(cfg, agent().get(&format!("{}/api/download/{}", cfg.server, cfg.edition))).call() {
            Ok(response) => response,
            Err(error) => return finish(Err(error)).map(|_| unreachable!()),
        };
        let temp = mods.join(".Pain.jar.part");
        let mut file = fs::File::create(&temp).map_err(|e| e.to_string())?;
        let mut reader = response.into_reader();
        let mut hasher = Sha256::new();
        let mut buffer = vec![0u8; 1 << 16];
        let mut got: u64 = 0;
        loop {
            let read = reader.read(&mut buffer).map_err(|e| format!("Скачивание оборвалось: {e}"))?;
            if read == 0 {
                break;
            }
            file.write_all(&buffer[..read]).map_err(|e| e.to_string())?;
            hasher.update(&buffer[..read]);
            got += read as u64;
            let _ = tx.send(Event::Progress(got, size.max(1)));
        }
        drop(file);
        let actual: String = hasher.finalize().iter().map(|b| format!("{b:02x}")).collect();
        if actual != wanted {
            let _ = fs::remove_file(&temp);
            return Err("Клиент скачался повреждённым. Попробуй ещё раз.".to_owned());
        }
        // прежние файлы клиента убираем, чтобы мод не загрузился дважды
        if let Ok(entries) = fs::read_dir(&mods) {
            for entry in entries.flatten() {
                let name = entry.file_name().to_string_lossy().to_lowercase();
                if name.starts_with("pain") && name.ends_with(".jar") {
                    let _ = fs::remove_file(entry.path());
                }
            }
        }
        fs::rename(&temp, &pain).map_err(|e| e.to_string())?;
    }

    let jvm_args = profile["arguments"]["jvm"].as_array().map(|a| a.iter().filter_map(|v| v.as_str().map(str::to_owned)).collect()).unwrap_or_default();
    Ok(Installed {
        classpath: full,
        main_class: profile["mainClass"].as_str().unwrap_or("net.fabricmc.loader.impl.launch.knot.KnotClient").to_owned(),
        asset_index: index_id,
        jvm_args,
        fabric_version: profile["id"].as_str().unwrap_or("fabric").to_owned(),
    })
}

/// Java 21 или новее: из PATH, JAVA_HOME или из папки соседнего лаунчера.
pub fn find_java() -> Result<PathBuf, String> {
    let exe = if cfg!(windows) { "java.exe" } else { "java" };
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Some(java_home) = std::env::var_os("JAVA_HOME") {
        candidates.push(PathBuf::from(java_home).join("bin").join(exe));
    }
    candidates.push(PathBuf::from(exe));
    if let Ok(entries) = fs::read_dir(home().join(".minecraftx/jre")) {
        for entry in entries.flatten() {
            candidates.push(entry.path().join("bin").join(exe));
        }
    }
    for candidate in candidates {
        if let Ok(output) = Command::new(&candidate).arg("-version").output() {
            let text = String::from_utf8_lossy(&output.stderr).to_string() + &String::from_utf8_lossy(&output.stdout);
            let major = text.split('"').nth(1).and_then(|v| v.split('.').next()).and_then(|n| n.parse::<u32>().ok()).unwrap_or(0);
            if major >= 21 {
                return Ok(candidate);
            }
        }
    }
    Err("Не нашлась Java 21 или новее. Поставь её (в Arch: sudo pacman -S jre21-openjdk) и запусти лаунчер снова.".to_owned())
}

fn offline_uuid(nick: &str) -> String {
    // любой постоянный идентификатор: сервер без лицензии всё равно считает свой по нику
    let digest = Sha1::digest(format!("OfflinePlayer:{nick}").as_bytes());
    let h: String = digest.iter().take(16).map(|b| format!("{b:02x}")).collect();
    format!("{}-{}-{}-{}-{}", &h[0..8], &h[8..12], &h[12..16], &h[16..20], &h[20..32])
}

/// Запускает игру и пересылает её вывод в журнал, пока она работает.
pub fn launch(cfg: &Config, installed: &Installed, tx: &Tx) -> Result<(), String> {
    let java = find_java()?;
    let game = cfg.game_dir();
    fs::create_dir_all(&game).map_err(|e| e.to_string())?;
    let separator = if cfg!(windows) { ";" } else { ":" };
    let classpath = installed.classpath.iter().map(|p| p.to_string_lossy().into_owned()).collect::<Vec<_>>().join(separator);
    let mut command = Command::new(java);
    command.current_dir(&game).arg(format!("-Xmx{}G", cfg.ram_gb)).arg("-Xms512M");
    for arg in &installed.jvm_args {
        command.arg(arg);
    }
    command
        .arg("-cp")
        .arg(classpath)
        .arg(&installed.main_class)
        .args(["--username", &cfg.nick, "--version", &installed.fabric_version, "--gameDir"])
        .arg(&game)
        .arg("--assetsDir")
        .arg(cfg.root.join("assets"))
        .args(["--assetIndex", &installed.asset_index, "--uuid", &offline_uuid(&cfg.nick), "--accessToken", "0", "--userType", "legacy", "--versionType", "Pain"])
        .stdout(Stdio::piped())
        .stderr(Stdio::piped());
    // для разработки: PAIN_EXTRA_ARGS — дополнительные параметры игры, например сразу зайти в мир
    if let Ok(extra) = std::env::var("PAIN_EXTRA_ARGS") {
        for arg in extra.split('|').filter(|a| !a.is_empty()) {
            command.arg(arg);
        }
    }
    let mut child = command.spawn().map_err(|e| format!("Не удалось запустить Java: {e}"))?;
    let _ = tx.send(Event::Status("Игра запущена".to_owned()));
    if let Some(stderr) = child.stderr.take() {
        let tx = tx.clone();
        std::thread::spawn(move || {
            for line in BufReader::new(stderr).lines().map_while(Result::ok) {
                let _ = tx.send(Event::Log(line));
            }
        });
    }
    if let Some(stdout) = child.stdout.take() {
        for line in BufReader::new(stdout).lines().map_while(Result::ok) {
            let _ = tx.send(Event::Log(line));
        }
    }
    let status = child.wait().map_err(|e| e.to_string())?;
    if status.success() {
        Ok(())
    } else {
        Err(format!("Игра закрылась с ошибкой ({status}). Подробности — в журнале ниже."))
    }
}
