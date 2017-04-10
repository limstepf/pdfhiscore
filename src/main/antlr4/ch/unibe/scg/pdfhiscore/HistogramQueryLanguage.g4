grammar HistogramQueryLanguage;

prog: AND expr+
	| OR expr+
	| GT value+ INT
	| expr+
	;

expr: '(' AND expr+ ')'
	| '(' OR expr+ ')'
    | '(' GT value+ INT ')'
	| NOT expr
    | value
	;

value: WORD | SQWORD | DQWORD;

AND:	'&&';
OR:		'||';
NOT:	'!';
GT:		'>';

SQWORD:	'\'' .*? '\'';
DQWORD:	'"' .*? '"';
WORD:	ALPHA (ALPHA|DIGIT)*;
INT:	DIGIT+;

fragment ALPHA:	[a-zA-Z_\-];
fragment DIGIT:	[0-9];

WS: [ \t\r\n]+ -> skip;
