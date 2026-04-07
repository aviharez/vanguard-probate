# Cross-Border Asset Valuation Tool

A standalone CLI utility for **Vanguard Cross-Border Estates** that calculates the USD equivalent of foreign-currency assets at a specific historical date, for use in international probate and court filings.

## Features

| Capability | Detail |
|------------|--------|
| API | [Frankfurter](https://www.frankfurter.app/) (European Central Bank rate) - no API key required |
| Historical lookup | Any date from 1999-01-04 to present |
| Business-day fallback | Automatically resolves weekends and ECB non-publishing days to the nearest prior rate |
| Valuation Memo | Generates a formatted `.txt` or `.pdf` file ready for inclusion in a legal case file |
| Currency validation | Validates the supplied ISO 4217 code against the live Frankfurter currency list |
| Error Handling | Clear messages for unreachable API, unsupported currencies, invalid inputs |

## Prerequisites

| Requirement | Version |
|-------------|---------|
| Java (JDK) | 17 or higher |
| Maven | 3.6 or higher |
| Internet access | Required to call the Frankfurter API |

## Build

```bash
# from the project root directory
mvn clean package
```

This produces a self-contained executable JAR at:
```
target/vanguard-probate-1.0.0.jar
```

## Run
```bash
java -jar target/vanguard-probate-1.0.0.jar
```

## Walkthrough

The tool will prompt for five inputs:
```
    > Case Reference (e.g., Estate of J. Smith -- Case No. 2024-PRB-001): Estate of J. Smith -- Case No. 2024-PRB-001
    > Asset Description (e.g., Barclays Bank Account, London): Barclays Bank Account, London
    > Date of Death / Valuation Date (YYYY-MM-DD): 2015-10-14
    > Source Currency Code (ISO 4217, e.g., GBP, EUR, JPY): GBP
    > Asset Amount in GBP: 21000
```

**Console output:**

```
  +-----------------------------------------------------+
  | VALUATION RESULT                                    |
  +-----------------------------------------------------+
  | Source Amount : GBP 21,000.00                       |
  | Rate Date     : 2015-10-14                          |
  | Rate Used     : 1 GBP = 1.538143 USD                |
  | USD VALUE     : 32,301.00                           |
  +-----------------------------------------------------+

  Memo (TXT) saved: Valuation_Memo_Estate_of_J_Smith_--_Case_No_2024-PRB-00_2015-10-14_GBP.txt
```

**Generated memo** (`Valuation_Memo_...txt`) excerpt:

```
================================================================================
VANGUARD CROSS-BORDER ESTATES
ASSET VALUATION MEMORANDUM
================================================================================

  CASE REFERENCE:                   Estate of J. Smith -- Case No. 2024-PRB-001
  MEMO DATE:                        April 7, 2026
  PREPARED BY:                      Cross-Border Asset Valuation Tool

--------------------------------------------------------------------------------
  ASSET DESCRIPTION:                Barclays Bank Account, London
--------------------------------------------------------------------------------

  SOURCE CURRENCY:                  GBP  (British Pound Sterling)
  ORIGINAL AMOUNT:                  £21,000.00

  VALUATION DATE (DATE OF DEATH):   October 14, 2015  [2015-10-14]
  RATE DATE USED:                   October 14, 2015  [2015-10-14]
  NOTE:                             Rate date matches requested valuation date.

  EXCHANGE RATE:                    1 GBP = 1.538143 USD
  RATE SOURCE:                      Frankfurter API (European Central Bank reference rates)

--------------------------------------------------------------------------------
  USD EQUIVALENT VALUE:             $32,301.00
--------------------------------------------------------------------------------
```

## Business-Day Fallback

When a date falls on a weekend or ECB non-publishing day, the tool automatically resolves to the nearest prior valid rate and flags this in the memo:

```
 NOTE: The requested valuation date (2024-03-16) fell on a weekend or ECB
       non-publishing day. The nearest preceding business day rate (2024-03-15)
       has been applied in accordance with standard probate practice.
```

## API Configuration

No API key is required. Frankfurter is a free, open-source service.