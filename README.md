# PDF Hi(stogram) Score

`pdfhiscore` is a command line tool to analyse and score (and thereby sort/filter), or just search PDF files by evaluating Histogram Query Language (HQL) expressions that query word histogram(s) extracted from the PDF files. The histogram query itself is composed of multiple, weighted histogram query expressions.


## Use Case/Workflow

What `pdfhiscore` does for you, given a list of PDF files and a histogram query:

1. For all PDF files: 
  1. Extract all text of the PDF file with [Apache PDFBox](https://pdfbox.apache.org/).
  2. Create two word histograms:
    1. A (single) word histogram, and
	2. A compound word histogram, where a *compound* word is composed of two (single) words that consecutively appeared in the text.
  3. Parse the histogram query, and extract all words (single and compound).
  4. Evaluate all expressions in the histogram query, and calculate the *hiscore*, which is the sum of the weights of all expressions that evaluated to `true`.
  5. Write the `pdfhiscore` report file (incl. query matches, or the absolute frequency of words used by any of the expressions).
2. Write the `pdfhiscore` summary file (incl. some descriptive statistics about the *hiscore*, absolute and relative frequencies of expressions (that evaluated to `true`) and words (used by any of the expressions) that occured at least once per PDF file).

Report and summary files are written in the human-readable structured data format `yaml`. Further processing is left to the user (and probably a bit more fun if the PDF files come together with their BibTeX entry).

And...

1. It also let's you search a bunch of PDF files by evaluating ad hoc histogram query expression.


**Question:** 
> So.. this is just another shitty, and rather slow tool to search multiple PDF files?

Pretty much, yes.


## Histogram Query Language (HQL) Grammar

The HQL grammar defines a simple boolean language in prefix notation to query/evaluate word histograms. Words, or compound words, are the basic values, and evaluate to the number of times they're occuring in the histogram (or just to   `true`, if a word occurs at least once). That is, the statement `word` is equivalent to `GT word 0` (i.e. the word exists, or occurs at least once in the histogram). `GT` stands for greater than, and is one of the four implemented operators, besides the usual logical and `&&`, logical or `||`, and logical not `!`.

```
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

AND:    '&&';
OR:     '||';
NOT:    '!';
GT:     '>';

SQWORD: '\'' .*? '\'';
DQWORD: '"' .*? '"';
WORD:   ALPHA (ALPHA|DIGIT)*;
INT:    DIGIT+;

fragment ALPHA: [a-zA-Z_\-];
fragment DIGIT: [0-9];

WS: [ \t\r\n]+ -> skip;
```

### Histogram Query Language (HQL) Preprocessor

Some (query) language features are implemented with a simple preprocessor:

* Comments: line starting with an `#` are ignored.
* Line breaks: expression ending with `/` are continued on the next line.
* Options: a line may start with an options block in the form of `[var=val, ...] <expression>`
  - Currently the only used value is `weight` whose value can be a positive or negative `double`. The weight is used to make up the *hiscore* in case the expression evaluates to `true`. The default weight is one.


### Example Histogram Query Expressions

* `|| w1 w2 w3`: any of the words `w1`, `w2`, or `w3` has to occur at least once.
* `&& w1 w2 w3`: all three words have to occur (each) at least once. This is equivalent to the expression: `w1 w2 w3` (without the and operator).
* `&& w1 !w2`: the first word should occur, but not the second.
* `> w1 w2 w3 10`: the sum of the frequencies of the three words has to be at least eleven.
* `(&& (|| w1 w2) w3)`: the word `w3` and either `w1` or `w2` have to occur at least once.
* ...and so on. You get the idea.

  
## Usage

```
$ java -jar pdfhiscore-1.0.0-SNAPSHOT.jar <options>

-d, --directory <directory>
    The directory to recursively find and process all PDF files.

-f, --file <file>
    The PDF file to process.

-q, --query <file>
    The histogram query containing Histogram Query Language (HQL) expressions.

-c, --config <string> (DEFAULT = "summary+reports")
    The config string ("summary", "reports", "explain", "histograms", "verbose").

-s, --search <string>
    A Histogram Query Language (HQL) expression to search the given files. 

-u, --usage
    Print the usage of this program.
```



## Examples

There are two basic modes of operation: `query` and `search`. The firmer is the main operation that writes the report and summary files (for further processing), while the latter just offers a quick method to search a bunch of PDF files (by default no files are being written to, the output is perfectly fine `yaml` and can be copied/redirected for further processing if desired).


### `query` Example

```bash
#!/bin/bash
app=./pdfhiscore.git/target/pdfhiscore-1.0.0-SNAPSHOT.jar
dir=./pdf-files
query=./query.hql
cfg=summary+reports+explain+histograms+verbose
log=./pdfhiscore.log

java -jar ${app} --directory ${dir} --query ${query} --config ${cfg} | tee ${log}
```

#### `query.hql` Example

```
# histogram query
[weight=2] || "software visualization" "software visualisation" softvis \
	"program visualization" "program visualization" \
	"code visualization" "code visualisation"
[weight=1] || "information visualization" "information visualisation" infovis
[weight=1] || "scientific visualization" "scientific visualisation" scivis
# ...
|| review "systematic review" \
	"mapping study" "systematic mapping"
```


#### `summary.pdfhiscore.yaml` Example

```yaml
{
  query-date: !!timestamp '2017-04-09T23:05:18.644Z',
  query-job-size: 1130,
  query-num-expressions: 20,
  query: {
    ? '|| "software visualization" "software visualisation" softvis "program visualization"
      "program visualization" "code visualization" "code visualisation"'
    : {
      abs-frequency: 886,
      rel-frequency: 0.784070796460177
    },
    '|| "information visualization" "information visualisation" infovis': {
      abs-frequency: 309,
      rel-frequency: 0.2734513274336283
    },
	# ...
    '|| review "systematic review" "mapping study" "systematic mapping"': {
      abs-frequency: 336,
      rel-frequency: 0.2973451327433628
    }
  },
  query-matches: {
    software visualization: {
      abs-frequency: 793,
      rel-frequency: 0.7017699115044248
    },
    software visualisation: {
      abs-frequency: 96,
      rel-frequency: 0.08495575221238938
    },
	# ...
    review: {
      abs-frequency: 332, # probably should be replaced by 'literature review'
      rel-frequency: 0.2938053097345133
    },
    systematic review: {
      abs-frequency: 27,
      rel-frequency: 0.023893805309734513
    },
    mapping study: {
      abs-frequency: 14,
      rel-frequency: 0.012389380530973451
    },
    systematic mapping: {
      abs-frequency: 15,
      rel-frequency: 0.01327433628318584
    }
  },
  pdf-text-extraction-failures: [
    .\pdf-files\344_Kimelman%3A1994%3ASMV%3A951087.951120.pdf,
    .\pdf-files\752_6650518.pdf,
    .\pdf-files\828_Baker1995119.pdf]
  ,
  query-min-score: 0.0,
  query-max-score: 24.0,
  score: {
    N: 1130,
    min: 0.0,
    max: 21.0,
    median: 11.0,
    mean: 11.008849557522122,
    standard-deviation: 4.540273227171135,
    variance: 20.614080977366992,
    skewness: -0.29564798567848377,
    kurtosis: -0.43229660007426407
  },
  score-normalized: { 
    # ...
  },
  score-cut-normalized: {
    # ...
  },
  scores: {
    1000_Shanti2014126.pdf: [
      8.0,
      0.3333333333333333,
      0.3333333333333333]
    ,
    1001_Ganesan2016345.pdf: [
      8.0,
      0.3333333333333333,
      0.3333333333333333]
    ,
	# ...
    9_Ashford%3A2011%3ADVS%3A2459296.2459311.pdf: [
      11.0,
      0.4583333333333333,
      0.4583333333333333]
    
  }
}
```

For each expression of the query a "hit rate" (abs. and rel. frequencies the expression evaluated to `true`) is given, s.t. the query may be improved more easily. And for all words (contained in an expression) `query-matches` lists the abs. and rel. frequencies of whether or not that word occured in the extracted text of a PDF file. 

Next `pdf-text-extraction-failures` are listed, which are usually PDF files containing nothing but images (e.g. scanned papers). 

What follows are some descriptive statistics about the calculated *hiscore*. `score-normalized` is normalized to the range `0..1` (assuming no negative weights, otherwise the lower bound is the total sum of negative weighted expressions), and `score-cut-normalized` is normalized to the range `0..1` even in any case, since negative *hiscores* are set to zero.

Finally the individual *hiscores* are listed for each PDF file.



#### `report.pdfhiscore.yaml` Example

```yaml
{
  query-date: !!timestamp '2017-04-09T23:05:18.644Z',
  file-info: {
    name: 776_7332417.pdf,
    path: '.\pdf-files\776_7332417.pdf',
    size: 401008,
    last-modified: !!timestamp '2017-03-31T02:52:59.459Z'
  },
  pdf-info: {
    subject: VISSOFT 2015,
    title: main2,
    author: Alexandre Bergel,
    creator: Preview,
    producer: Mac OS X 10.10.5 Quartz PDFContext,
    keywords: Feature Modeling;  Visualization;  Composition;  Software Product Lines,
    creation-date: !!timestamp '2015-09-04T17:02:27Z',
    modification-date: !!timestamp '2015-11-19T16:34:24Z',
    num-pages: '10'
  },
  total-score: 17.0,
  total-score-normalized: 0.7083333333333334,
  total-score-cut-normalized: 0.7083333333333334,
  query-matches: {
    case study: 4,
    effectiveness: 2,
    efficiency: 2,
    experiments: 2,
    case studies: 2,
    software visualization: 2,
    experiment: 2,
    review: 2,
    evaluate: 2,
    industry: 1,
    controlled experiments: 1,
    companies: 1,
    feasibility study: 1,
    survey: 1,
    softvis: 1,
    validate: 1
  },
  single-word-count: 5383
}
```

The report file created for each PDF file contains some general information about the file, some more specific information about the file as a PDF file, the computed *hiscore* and the `query-matches`, which is the abs. frequency a word (contained in an expression) occured in the text.

The `config` option lets you customize this a bit: 

* the full histograms can be requested by including `histograms` in the config string (further processing with many files might be much slower now, so be warned).
* the single expressions can be listed and `explain`ed.
* ...and while we're at it: `summary` and `reports` let you toggle the creation of the summary and report files, while `verbose` doesn't shut off most of PDFBox's chatter and warnings.



### `search` Example

```bash
#!/bin/bash
app=./pdfhiscore.git/target/pdfhiscore-1.0.0-SNAPSHOT.jar
dir=./pdf-files
search="|| 'literature review' 'systematic review' 'mapping study' 'systematic mapping'"
log=./pdfhiscore-search.log

java -jar ${app} --directory ${dir} --search ${search} | tee ${log}
```

Let's say we want to have a look at those 336 papers that could be (or at least talk about) systematic reviews or a mapping study. That's what the `search` option is for. Instead of a (full) query, it takes a single expression and returns a list of all PDF files that evaluated to `true`.


#### Example `search` output

```yaml
search-query: "|| 'literature review' 'systematic review' 'mapping study' 'systematic mapping'"
num-documents: 1130
search-results:
  - name: '1032_Sajaniemi200715.pdf'
    path: '.\pdf-files\1032_Sajaniemi200715.pdf'
    num-pages: 8
    query-matches: {
      'literature review': 1
    }
  - name: '1052_Behutiye2017139.pdf'
    path: '.\pdf-files\1052_Behutiye2017139.pdf'
    num-pages: 20
    query-matches: {
      'systematic mapping': 5,
      'mapping study': 3,
      'literature review': 5,
      'systematic review': 2
    }
  - name: '1076_Smith2006313.pdf'
    path: '.\pdf-files\1076_Smith2006313.pdf'
    num-pages: 9
    query-matches: {
      'systematic review': 1
    }
	# ...
  - name: '936_Shahin2014161.pdf'
    path: '.\pdf-files\936_Shahin2014161.pdf'
    num-pages: 25
    query-matches: {
      'systematic mapping': 2,
      'mapping study': 2,
      'literature review': 6,
      'systematic review': 2
    }
  - name: '943_Palmarini201723.pdf'
    path: '.\pdf-files\943_Palmarini201723.pdf'
    num-pages: 6
    query-matches: {
      'literature review': 1
    }
  - name: '96_Pleuss%3A2011%3AVTA%3A2019136.2019161.pdf'
    path: '.\pdf-files\96_Pleuss%3A2011%3AVTA%3A2019136.2019161.pdf'
    num-pages: 8
    query-matches: {
      'literature review': 3
    }
  - name: '980_Makishi2015e190.pdf'
    path: '.\pdf-files\980_Makishi2015e190.pdf'
    num-pages: 11
    query-matches: {
      'systematic review': 1
    }
document-matches: 60
```


## Related Projects

* [bibsani](https://github.com/limstepf/bibsani): Bib(TeX) Sani(tizer)
* [csvtobib](https://github.com/limstepf/csvtobib): Converts (IEEE) CSV to BibTeX
* [pdfdbscrap](https://github.com/limstepf/pdfdbscrap): PDF Database Scrap(er)
