# CSV and the estimation sheets under `sheets/`

## What it is

**CSV** means "comma-separated values". A CSV file is a plain text file that holds a table. Each line is one **row**. The cells of a row are separated by commas. The first row usually names the columns. Any spreadsheet program can open a CSV file as a table, and any program can read it as text.

CSV has no official, strict rule that all programs follow. The common habit, which spreadsheet programs follow when they export, is this: a cell that contains a comma, a double quote or a line break is wrapped in double quotes, and a double quote inside such a cell is written twice. This repository's reader follows that habit.

## Why this repository uses it

An estimate is a list of assumptions, and each assumption has a number, a unit and a reason. A table fits that shape. A CSV file is easy to open in a spreadsheet, easy to diff in Git, and needs no library to read. Every number on a sheet carries the unit it is written in and the source it comes from, so the sheet itself teaches the habit that Module 3 is about: no number without its unit and its reason.

## Where it appears

| File | What it is |
|---|---|
| `sheets/seatwave-production.csv` | PRODUCTION: the national SeatWave, with the national tour on sale. 5,000,000 daily users. Accepted. |
| `sheets/seatwave-simple.csv` | SIMPLE: one region, 20,000 daily users, no national on-sale. Accepted. |
| `sheets/lab1-unit-slips.csv` | Lab 1: the PRODUCTION sheet with three unit slips and one missing line. The program rejects it. |
| `sheets/photo-sharing.csv` | Lab 3 solution: a photo-sharing app with 50 million daily users. Accepted. |
| `sheets/photo-sharing-given.csv` | Lab 3 as handed to the learner: the same sheet with a factor-of-1,000 mistake. Accepted, and wrong. |
| `src/main/java/io/plainround/seatwave/estimates/estimate/CsvSheet.java` | Reads the lines and writes them back. See `java-estimate-walkthrough.md`. |
| `src/main/java/io/plainround/seatwave/estimates/estimate/InputsReader.java` | Checks each entry against the input rules. |

## How it works underneath

The sheet is a text file on disk. `EstimateCli` reads the whole file into memory as a list of lines (`Files.readAllLines`, decoded as UTF-8, and refused if it is larger than 1,000,000 bytes). `CsvSheet.parse` turns the lines into entries. `InputsReader.read` checks the entries. Nothing is written back to the file. The report prints the inputs again as a valid sheet, so you can save them.

**The format of this sheet, line by line.**

- A line that starts with `#`, and a blank line, are **comments**. They are skipped.
- The first line that is not a comment must be exactly the header `quantity,value,unit,source`.
- Every other line has four columns: `quantity`, `value`, `unit` and `source`.
- The **source** is last, so it may contain commas. The reader cuts a line at the first three commas only (`line.split(",", 4)`), and the rest is the source. A spreadsheet program, which does not know that rule, writes such a cell in double quotes, so the sheets do so too. The reader removes a pair of quotes around the source and turns each doubled quote back into one.
- A **value** is a plain decimal number: digits, and an optional dot with more digits. No sign, no exponent, no thousands comma. Underscores may group digits, so `5_000_000` is five million. The reader removes the underscores before it converts.
- The **unit** must be written exactly as the input requires (the table below). `KB` is not accepted anywhere, because it can mean 1,000 or 1,024 bytes.
- The **quantity** is the name of an input (`peak_factor`), or the name of one number of a store, written `store.<name>.<field>`.

**A store.** A store is one kind of stored data, such as bookings, logs or photos. Each one needs five numbers: `items_per_day`, `bytes_per_item`, `retention_days`, `replication_factor` and `index_overhead`. A sheet needs at least one store. `store.bookings.bytes_per_item` is the size of one booking, for the store named `bookings`. A store name is 1 to 32 characters: lowercase letters, digits and underscores, starting with a letter.

**The table of inputs.** Each input has one unit and one range. The rule lives in `InputRule` and `StoreField`.

| Quantity | Unit | Range |
|---|---|---|
| `daily_active_users` | users | greater than 0 |
| `requests_per_user_per_day` | requests/user/day | greater than 0 |
| `peak_factor` | ratio | 1 or more |
| `write_share` | percent | 0 to 100 |
| `request_bytes`, `response_bytes` | bytes | greater than 0 |
| `horizon_years` | years | greater than 0 |
| `store.<name>.items_per_day` | items/day | 0 or more |
| `store.<name>.bytes_per_item` | bytes | greater than 0 |
| `store.<name>.retention_days` | days | greater than 0 |
| `store.<name>.replication_factor` | copies | 1 or more |
| `store.<name>.index_overhead` | ratio | 1 or more |
| `cache_keys` | keys | 0 or more |
| `cache_hot_share` | percent | 0 to 100 |
| `cache_entry_bytes` | bytes | greater than 0 |
| `cache_overhead` | ratio | 1 or more |
| `knee_per_server` | requests/s | greater than 0 |
| `utilization_target` | percent | above 0 and at most 100 |
| `headroom` | percent | 0 or more |
| `spare_servers` | servers | a whole number, 0 or more |
| `server_price` | USD/hour | 0 or more |
| `egress_price` | USD/GB | 0 or more |
| `storage_price` | USD/GB-month | 0 or more |

**Reading and validating, in two stages.**

1. `CsvSheet.parse` checks the **shape**: the header, four columns on every line, a plain decimal number in the value column, and a non-empty source. It reports every shape problem it finds, then stops: if any line has a shape problem, no entries come out, and stage 2 does not run. I made a sheet with `twenty` in a value column, a line with three columns and an empty source, and got `REJECTED line 5: 'twenty' is not a plain decimal number`, `REJECTED line 6: expected 4 columns: quantity,value,unit,source` and `REJECTED line 8: the source column is empty`.
2. `InputsReader.read` checks the **meaning**: the quantity is an input or a store field, it is given once, its unit is exactly the required one, its value is in range, nothing is missing, and every store has all five fields. It also reports every problem, in one run.

## Walkthrough of this repository's use

### One row

```text
peak_factor,3,ratio,Normal days only: the evening busy hour runs at about three times the daily average (assumed)
```

- `peak_factor` is the quantity: the busiest rate divided by the day's average rate.
- `3` is the value.
- `ratio` is the unit. The input `peak_factor` requires the unit `ratio`.
- `Normal days only: the evening busy hour runs at about three times the daily average (assumed)` is the source. The word `assumed` says that nobody has measured it. The source is free text. The program checks only that it is not empty: it cannot read a sentence and judge it.

A row with a source that contains a comma, as the spreadsheet writes it:

```text
server_price,0.2016,USD/hour,"Module 1: m7i.xlarge on demand, us-east-1, checked 8 November 2026"
```

- The source is wrapped in double quotes because it contains commas: `Module 1: m7i.xlarge on demand, us-east-1, checked 8 November 2026`. The reader removes the quotes.
- The value `0.2016` is a plain decimal. It is a price in `USD/hour`.

A row that uses underscores to group digits:

```text
daily_active_users,20_000,users,Module 1: 20_000 visits a day
```

- `20_000` is twenty thousand. The reader removes the underscore, and the printed report shows `20000`. The report writes each value with `toPlainString`, so a sheet printed by the report has no underscores. Both forms are valid.

### The five sheets

**`seatwave-production.csv` and `seatwave-simple.csv`.** Both are accepted, and both end with the exit status 0. PRODUCTION has four stores (bookings, seats, logs, posters), and SIMPLE has two (bookings, logs). The numbers in the report are in `java-estimate-walkthrough.md`. In short, PRODUCTION gives a peak of 30,093 requests/s and 66 servers at peak, and SIMPLE gives a peak of 13.89 requests/s and 1 server.

**`lab1-unit-slips.csv`.** It is PRODUCTION with four deliberate faults. The program catches all four:

```text
response_bytes,305_696,bits,"The seat-map team quoted the average response in bits: 38,212 bytes x 8"
```

```text
store.bookings.bytes_per_item,2,KB,2 KB per booking row
```

```text
knee_per_server,69_120_000,requests/day,The Module 1 knee of 800 requests/s written per day: 800 x 86_400
```

- Line 9 writes the response size in `bits` where `bytes` are required. 38,212 bytes x 8 = 305,696 bits.
- Line 12 writes `2` in the unit `KB`. The reader has no unit named `KB`, and does not guess whether it means 1,000 or 1,024.
- Line 35 writes the knee in `requests/day` where `requests/s` is required: 800 x 86,400 = 69,120,000.
- The fourth fault is a deleted line: the `headroom` line is not there.
- The program's answer, with exit status 1:

```
REJECTED line 9: response_bytes must be in bytes, not bits
REJECTED line 12: store.bookings.bytes_per_item must be in bytes, not KB
REJECTED line 35: knee_per_server must be in requests/s, not requests/day
REJECTED missing input: headroom
SHEET REJECTED: fix every line above; nothing was estimated.
```

**`photo-sharing.csv`.** The solution of Lab 3: a photo-sharing app with 50 million daily users, with three stores (photos, metadata, logs). It is accepted, and its estimate is 28,935 requests/s on average, 86,806 at peak, 12,505 GB of new data a day and 18,516 TB stored at the horizon.

**`photo-sharing-given.csv`: the planted mistake.** The file is the same as the solution, except for one line. Compare:

```text
store.photos.bytes_per_item,2_000_000,bytes,"About 2 MB: the original plus three resized copies (assumed)"
```

```text
store.photos.bytes_per_item,2_000,bytes,"About 2,000 KB: the original plus three resized copies (assumed)"
```

- In the solution, a photo is `2_000_000` bytes: about 2 MB (the original plus three resized copies).
- In the given sheet, a photo is `2_000` bytes, and the source says `About 2,000 KB`. 2,000 KB is 2,000 x 1,000 = 2,000,000 bytes. The value was written as 2,000, which is the number of kilobytes, in the column for bytes. That is a factor-of-1,000 mistake.

**How the program reacts to it: it does not catch it.** The unit is `bytes`, which is the unit the input requires. The value 2,000 is greater than 0, which is in range. The source is not empty. Every rule passes. The sensitivity check doubles each input and finds no result that moves by more than 10 times, because the mistake is in the *size* of a number, not in a formula. So the program prints an estimate, with the line `No result moved more than 10 times when any one input doubled.` The program caught the unit slips of Lab 1 because a unit symbol can be compared with text. It cannot know that a photo is bigger than a text message. I ran both sheets:

| Line of the estimate | `photo-sharing-given.csv` (wrong) | `photo-sharing.csv` (right) |
|---|---|---|
| New photos data per day | 10 GB/day | 10,000 GB/day |
| All new data per day | 2,515 GB/day | 12,505 GB/day |
| Photos stored at the horizon | 18.25 TB | 18,250 TB |
| All stored at the horizon | 284 TB | 18,516 TB |
| Storage cost per month | 6,539.19 USD/month | 425,869.44 USD/month |

The rate and the bandwidth lines are identical in both runs, because the photo size is not in them. The way to catch the mistake is to check each line of the estimate against something you know: 50 million users who upload 5 million photos a day cannot add only 10 GB a day, because a single phone photo is a few megabytes. The sources on a sheet are what make that check possible: the source on line 12 of the given sheet says `2,000 KB`, and that does not match the number in the bytes column.

## Try it yourself

Run these in `seatwave-estimates/`.

1. Print a sheet with line numbers, and look at the comments, the header and a row:

```
cat -n sheets/seatwave-simple.csv | head -8
```

You see two comment lines, the header `quantity,value,unit,source` on line 3, and the first rows.

2. Run the program on the Lab 1 sheet:

```
./gradlew -q estimate --args='sheets/lab1-unit-slips.csv'
```

I got the five lines quoted above and, from Gradle, `finished with non-zero exit value 1`.

3. Find the planted mistake with `diff`:

```
diff sheets/photo-sharing.csv sheets/photo-sharing-given.csv
```

I got three changed lines in two groups: the two comment lines at the top (lines 1 and 2), and line 12, `store.photos.bytes_per_item`, with `2_000_000` on the left and `2_000` on the right.

4. Run the given sheet and see that it is accepted:

```
./gradlew -q estimate --args='sheets/photo-sharing-given.csv' | sed -n '/FIVE-LINE/,/COST/p'
```

I got `2. SIZE      2,515 GB/day of new data` and `3. STORAGE   284 TB stored at the horizon; 6,539.19 USD/month`. Run it again with `sheets/photo-sharing.csv` and compare: I got `12,505 GB/day` and `18,516 TB ... 425,869.44 USD/month`.

5. Make your own mistake. Copy `sheets/seatwave-simple.csv` to `build/neg.csv` (the `build` folder is not tracked by Git), change the `peak_factor` value to `-1`, and run it:

```
./gradlew -q estimate --args='build/neg.csv'
```

I got `REJECTED line 6: '-1' is not a plain decimal number` and the closing line. The value pattern allows no sign.

## Mistakes people make

- **Writing a value with a thousands comma.** `5,000,000` is read as three columns. Write `5_000_000` or `5000000`.
- **Writing a source with a comma and no quotes in a spreadsheet.** A spreadsheet program then splits it. This reader copes, because the source is the last column, but write the quotes as a spreadsheet does.
- **Leaving out the unit, or writing a friendly one.** `KB`, `MB` and `bits` are all rejected where `bytes` is required.
- **Trusting a sheet because the program accepted it.** The program checks units and ranges. It cannot check that a number is plausible. Read the estimate.
- **Writing the read share.** The sheet takes `write_share`, so doubling it in the sensitivity check is a fair test.
- **Duplicate lines.** The second one is rejected: `daily_active_users was already given on line 3`.
- **An empty source.** A number nobody can trace is rejected.

## Check yourself

1. How many columns has a sheet line, and which comes last? Answer: four: quantity, value, unit, source. The source is last, so it may contain commas.
2. What does `5_000_000` mean in a sheet? Answer: five million. The reader removes the underscores before it converts the number.
3. Why is the planted mistake not rejected? Answer: the unit is right, the value is in range and the source is not empty. The mistake is the size of the number, which only a person who knows that a photo is about 2 MB can see.
4. Which problems does the program report when a sheet has an unknown quantity, a duplicate line and a missing input? Answer: all of them, in one run: an `UnknownQuantity`, a `Duplicate` and a `Missing` problem. The reader collects every problem.
5. A line has only three columns. Which stage reports it, and does the second stage run? Answer: the first stage, `CsvSheet.parse`, reports `expected 4 columns`. The second stage does not run, because a shape problem rejects the sheet before the inputs are checked.

## Further reading

- RFC 4180, the common format of CSV files, from the Internet Engineering Task Force, https://www.rfc-editor.org/rfc/rfc4180