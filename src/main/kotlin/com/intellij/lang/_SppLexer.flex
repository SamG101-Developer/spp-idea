package com.intellij.lang;

import com.intellij.lexer.FlexLexer;
import com.intellij.psi.tree.IElementType;

import static com.intellij.psi.TokenType.BAD_CHARACTER;
import static com.intellij.psi.TokenType.WHITE_SPACE;
import static com.intellij.lang.psi.SppTypes.*;

%%

%{
  public _SppLexer() {
    this((java.io.Reader)null);
  }
%}

%public
%class _SppLexer
%implements FlexLexer
%function advance
%type IElementType
%unicode

EOL=\R
WHITE_SPACE=\s+

LINE_COMMENT=#.*
LEXEME_IDENTIFIER=[a-z][_a-zA-Z0-9]*
LEXEME_UPPER_IDENTIFIER=[A-Z][_a-zA-Z0-9]*
LEXEME_BIN_DIGITS=0b[01]+
LEXEME_OCT_DIGITS=0o[0-7]+
LEXEME_HEX_DIGITS=0x[0-9a-fA-F]+
LEXEME_DEC_DIGITS=[0-9]+
LEXEME_SINGLE_QUOTE_STR=b?'[^'\r\n]+'
LEXEME_DOUBLE_QUOTE_STR=b?\"[^\"\r\n]*\"

%%
<YYINITIAL> {
  {WHITE_SPACE}                   { return WHITE_SPACE; }

  "{"                             { return TOKEN_LEFT_CURLY_BRACE; }
  "}"                             { return TOKEN_RIGHT_CURLY_BRACE; }
  "["                             { return TOKEN_LEFT_SQUARE_BRACKET; }
  "]"                             { return TOKEN_RIGHT_SQUARE_BRACKET; }
  "("                             { return TOKEN_LEFT_PARENTHESIS; }
  ")"                             { return TOKEN_RIGHT_PARENTHESIS; }
  ":"                             { return TOKEN_COLON; }
  ","                             { return TOKEN_COMMA; }
  "="                             { return TOKEN_ASSIGN; }
  "@"                             { return TOKEN_AT; }
  "_"                             { return TOKEN_UNDERSCORE; }
  "<"                             { return TOKEN_LESS_THAN; }
  ">"                             { return TOKEN_GREATER_THAN; }
  "+"                             { return TOKEN_ADD; }
  "-"                             { return TOKEN_SUB; }
  "*"                             { return TOKEN_MUL; }
  "/"                             { return TOKEN_DIV; }
  "%"                             { return TOKEN_REM; }
  "|"                             { return TOKEN_BIT_IOR; }
  "^"                             { return TOKEN_BIT_XOR; }
  "&"                             { return TOKEN_BIT_AND; }
  "."                             { return TOKEN_DOT; }
  "?"                             { return TOKEN_QUESTION_MARK; }
  "!"                             { return TOKEN_EXCLAMATION_MARK; }
  ";"                             { return TOKEN_SEMICOLON; }
  "'"                             { return TOKEN_SINGLE_QUOTE; }
  "\""                            { return TOKEN_DOUBLE_QUOTE; }
  "$"                             { return TOKEN_DOLLAR; }
  "->"                            { return TOKEN_ARROW_RIGHT; }
  ".."                            { return TOKEN_DOUBLE_DOT; }
  "::"                            { return TOKEN_DOUBLE_COLON; }
  "!!"                            { return TOKEN_DOUBLE_EXCLAMATION_MARK; }
  "=="                            { return TOKEN_EQUALS; }
  "!="                            { return TOKEN_NOT_EQUALS; }
  "<="                            { return TOKEN_LESS_THAN_EQUALS; }
  ">="                            { return TOKEN_GREATER_THAN_EQUALS; }
  "+="                            { return TOKEN_ADD_ASSIGN; }
  "-="                            { return TOKEN_SUB_ASSIGN; }
  "*="                            { return TOKEN_MUL_ASSIGN; }
  "/="                            { return TOKEN_DIV_ASSIGN; }
  "%="                            { return TOKEN_REM_ASSIGN; }
  "**"                            { return TOKEN_POW; }
  "<<"                            { return TOKEN_BIT_SHL; }
  ">>"                            { return TOKEN_BIT_SHR; }
  "|="                            { return TOKEN_BIT_IOR_ASSIGN; }
  "^="                            { return TOKEN_BIT_XOR_ASSIGN; }
  "&="                            { return TOKEN_BIT_AND_ASSIGN; }
  "**="                           { return TOKEN_POW_ASSIGN; }
  "<<="                           { return TOKEN_BIT_SHL_ASSIGN; }
  ">>="                           { return TOKEN_BIT_SHR_ASSIGN; }
  "cls"                           { return KEYWORD_CLS; }
  "fun"                           { return KEYWORD_FUN; }
  "cor"                           { return KEYWORD_COR; }
  "sup"                           { return KEYWORD_SUP; }
  "ext"                           { return KEYWORD_EXT; }
  "mut"                           { return KEYWORD_MUT; }
  "use"                           { return KEYWORD_USE; }
  "cmp"                           { return KEYWORD_CMP; }
  "let"                           { return KEYWORD_LET; }
  "type"                          { return KEYWORD_TYPE; }
  "self"                          { return KEYWORD_SELF; }
  "case"                          { return KEYWORD_CASE; }
  "of"                            { return KEYWORD_OF; }
  "loop"                          { return KEYWORD_LOOP; }
  "in"                            { return KEYWORD_IN; }
  "to"                            { return KEYWORD_TO; }
  "else"                          { return KEYWORD_ELSE; }
  "gen"                           { return KEYWORD_GEN; }
  "with"                          { return KEYWORD_WITH; }
  "ret"                           { return KEYWORD_RET; }
  "exit"                          { return KEYWORD_EXIT; }
  "skip"                          { return KEYWORD_SKIP; }
  "is"                            { return KEYWORD_IS; }
  "as"                            { return KEYWORD_AS; }
  "or"                            { return KEYWORD_OR; }
  "and"                           { return KEYWORD_AND; }
  "not"                           { return KEYWORD_NOT; }
  "async"                         { return KEYWORD_ASYNC; }
  "true"                          { return KEYWORD_TRUE; }
  "false"                         { return KEYWORD_FALSE; }
  "res"                           { return KEYWORD_RES; }
  "caps"                          { return KEYWORD_CAPS; }
  "defer"                         { return KEYWORD_DEFER; }

  {LINE_COMMENT}                  { return LINE_COMMENT; }
  {LEXEME_IDENTIFIER}             { return LEXEME_IDENTIFIER; }
  {LEXEME_UPPER_IDENTIFIER}       { return LEXEME_UPPER_IDENTIFIER; }
  {LEXEME_BIN_DIGITS}             { return LEXEME_BIN_DIGITS; }
  {LEXEME_OCT_DIGITS}             { return LEXEME_OCT_DIGITS; }
  {LEXEME_HEX_DIGITS}             { return LEXEME_HEX_DIGITS; }
  {LEXEME_DEC_DIGITS}             { return LEXEME_DEC_DIGITS; }
  {LEXEME_SINGLE_QUOTE_STR}       { return LEXEME_SINGLE_QUOTE_STR; }
  {LEXEME_DOUBLE_QUOTE_STR}       { return LEXEME_DOUBLE_QUOTE_STR; }

}

[^] { return BAD_CHARACTER; }
