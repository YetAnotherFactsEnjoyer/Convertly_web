use std::fmt::Display;

use crate::{
    Result,
    lexer::{Lexer, grammar::Grammar},
};
use clap::{Parser, Subcommand, crate_authors, crate_description, crate_name, crate_version};
use colored::*;
use convertly::error::LexerError::FileError;

#[derive(Debug, PartialEq, Eq)]
enum MockTokenKind {
    Word,
    Number,
    Symbol,
}

impl Display for MockTokenKind {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        let data = match *self {
            Self::Word => format!("{:<7}", "Word").bright_cyan(),
            Self::Number => format!("{:<7}", "Number").bright_purple(),
            Self::Symbol => format!("{:<7}", "Symbol").bright_blue(),
        };
        f.write_str(&data.to_string())
    }
}

struct MockGrammar;

impl Grammar<MockTokenKind> for MockGrammar {
    fn match_token(&self, slice: &str) -> Option<(MockTokenKind, usize)> {
        let mut chars = slice.chars();
        let first = chars.next()?;

        if first.is_alphabetic() {
            let len = slice
                .chars()
                .take_while(|c| c.is_alphanumeric() || *c == '_')
                .map(|c| c.len_utf8())
                .sum();
            return Some((MockTokenKind::Word, len));
        }

        if first.is_ascii_digit() {
            let len = slice
                .chars()
                .take_while(|c| c.is_ascii_digit())
                .map(|c| c.len_utf8())
                .sum();
            return Some((MockTokenKind::Number, len));
        }

        Some((MockTokenKind::Symbol, first.len_utf8()))
    }
}

/// Convert any file to another with custom config files.
#[derive(Parser, Debug)]
#[command(name = crate_name!())]
#[command(author = crate_authors!(", "))]
#[command(version = crate_version!())]
#[command(about = crate_description!())]
pub struct Cli {
    #[command(subcommand)]
    pub command: Commands,
}

#[derive(Subcommand, Debug)]
pub enum Commands {
    /// Tokenize a file & display in stdout output.
    Tokenize {
        /// Path to the file to tokenize.
        #[arg(short, long)]
        path: String,
    },
}

impl Cli {
    pub fn run(self) -> Result<()> {
        match self.command {
            Commands::Tokenize { path } => tokenize(&path),
        }
    }
}

fn tokenize(file_path: &str) -> Result<()> {
    log::info!("Reading file: {file_path}");

    let source_code = match std::fs::read_to_string(file_path) {
        Ok(content) => content,
        Err(e) => {
            return Err(FileError(file_path.to_string(), e.to_string()).into());
        }
    };

    let grammar = MockGrammar;
    let mut lexer = Lexer::new(&source_code, &grammar);

    let tokens = lexer.tokenize()?;

    println!(
        "{} {} {}",
        "Successfully tokenized.".bright_green(),
        format!("{}", tokens.len()).bright_blue().bold().italic(),
        "tokens found:".bright_black()
    );
    for (i, token) in tokens.iter().enumerate() {
        let msg = format!(
            " {} Kind: {} | Text: {:<12} | Line: {:<3} Col: {}",
            format!("{i}.").white(),
            token.kind,
            format!("'{}'", token.lexeme).bright_yellow(),
            format!("{}", token.span.line).bright_cyan(),
            format!("{}", token.span.column).bright_cyan(),
        );
        println!("{}", msg.bright_black());
    }
    Ok(())
}
