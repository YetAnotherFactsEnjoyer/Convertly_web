mod cli;
mod lexer;

use clap::Parser;
use convertly::Result;

fn main() {
    env_logger::builder()
        .filter_level(log::LevelFilter::Info)
        .init();

    if let Err(e) = cli::Cli::parse().run() {
        log::error!("{e}");
        std::process::exit(1);
    }
}
