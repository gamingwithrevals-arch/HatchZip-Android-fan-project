use jni::objects::{JClass, JString};
use jni::sys::jstring;
use jni::JNIEnv;
use std::path::PathBuf;
use hatchzip_core::engine::{run, NoopObserver};
use hatchzip_core::session::{Options, PasswordProvider};

struct FixedPassword(Option<String>);
impl PasswordProvider for FixedPassword {
    fn password_for(&self, _archive: &std::path::Path) -> Option<String> {
        self.0.clone()
    }
}

#[no_mangle]
pub extern "system" fn Java_io_github_xevrion_hatchzip_RustBridge_extractArchive<'local>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    archive_path: JString<'local>,
    out_dir: JString<'local>,
    password: JString<'local>,
    delete_after: u8,
) -> jstring {
    let archive: String = match env.get_string(&archive_path) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("Error reading archive path: {e}")).unwrap().into_raw(),
    };
    let out: String = match env.get_string(&out_dir) {
        Ok(s) => s.into(),
        Err(e) => return env.new_string(format!("Error reading output dir: {e}")).unwrap().into_raw(),
    };
    let pw: String = env.get_string(&password).map(|s| s.into()).unwrap_or_default();
    let pw_opt = if pw.trim().is_empty() { None } else { Some(pw) };

    let mut opts = Options::default();
    opts.keep = delete_after == 0;
    opts.output_dir = Some(PathBuf::from(&out));

    let provider = FixedPassword(pw_opt);
    let observer = NoopObserver;

    let res = std::panic::catch_unwind(|| {
        run(&[PathBuf::from(&archive)], &opts, &provider, &observer)
    });

    let msg = match res {
        Ok(summary) => {
            if summary.failed == 0 {
                format!("Success! Extracted {} archive(s) seamlessly.", summary.succeeded)
            } else {
                format!("Failed to extract ({} failed). Check password or storage space.", summary.failed)
            }
        }
        Err(_) => "Engine panic during extraction.".to_string(),
    };

    env.new_string(msg).unwrap().into_raw()
}
