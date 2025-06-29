#!/usr/bin/perl -I/home/phil/perl/cpan/DataTableText/lib/ -I/home/phil/perl/cpan/GitHubCrud/lib/
#-------------------------------------------------------------------------------
# Push block memory code to GitHub.
# Philip R Brenan at gmail dot com, Appa Apps Ltd Inc., 2025
#-------------------------------------------------------------------------------
use v5.38;
use warnings FATAL => qw(all);
use strict;
use Carp;
use Data::Dump qw(dump);
use Data::Table::Text qw(:all);
use GitHub::Crud qw(:all);

my $repo      = q(blockMemory);                                                 # Repo
my $user      = q(philiprbrenan);                                               # User
my $home      = fpd q(/home/phil), $repo;                                       # Home folder
my $shaFile   = fpe $home, q(sha);                                              # Sh256 file sums for each known file to detect changes
my $wf        = q(.github/workflows/main.yml);                                  # Work flow on Ubuntu
my @ext       = qw(.java .pl);                                                  # Extensions of files to upload to github
my @otherJava = qw(../btreeBlock/Test.java);                                    # Java files from other folders

say STDERR timeStamp,  " push to github $repo";

for my $j(@otherJava)                                                           # Link to crucial files defined else where
 {say STDERR qx(ln -s $j) unless -e $j;
 }

push my @files, searchDirectoryTreesForMatchingFiles($home, @ext);              # Files to upload
        @files = changedFiles $shaFile,  @files;                                # Filter out files that have not changed
my @java = grep {m/\.java\Z/} @files;                                           # Changed java files

if (!@files)                                                                    # No new files
 {say "Everything up to date";
  exit;
 }

if  (1)                                                                         # Upload via github crud
 {for my $s(@files)                                                             # Upload each selected file
   {my $c = readBinaryFile $s;                                                  # Load file

    $c = expandWellKnownWordsAsUrlsInMdFormat $c if $s =~ m(README);            # Expand README

    my $t = swapFilePrefix $s, $home;                                           # File on github
    my $w = writeFileUsingSavedToken($user, $repo, $t, $c);                     # Write file into github
    lll "$w  $t";
   }
 }

writeFileUsingSavedToken($user, $repo, q(.config/geany/snippets.conf),          # Save the snippets file as this was the thing I missed most after a rebuild
                   readFile(q(/home/phil/.config/geany/snippets.conf)));
writeFileUsingSavedToken($user, $repo, q(.config/geany/keybindings.conf),       # Save the keybindings file for the same reason
                  readFile(q(//home/phil/.config/geany/keybindings.conf)));

if (@java)                                                                      # Write workflow to test java files
 {my @j = map {fn $_} @java;
  my $d = dateTimeStamp;
  my $c = q(com/AppaApps/Silicon);                                              # Package to classes folder
  my $j = join ', ', @j;                                                        # Java files
  my $y = <<"END";
# Test $d

name: Test
run-name: $repo

on:
  push:
    paths:
      - '**/main.yml'

concurrency:
  group: \${{ github.workflow }}-\${{ github.ref }}
  cancel-in-progress: true

jobs:

  test:
    permissions: write-all
    runs-on: ubuntu-latest

    strategy:
      matrix:
        task: [$j]

    steps:
    - uses: actions/checkout\@v4
      with:
        ref: 'main'

    - name: 'JDK 24'
      uses: oracle-actions/setup-java\@v1
      with:
        website: jdk.java.net

    - name: Java release
      run: |
        java -version

    - name: Position files in package
      run: |
        mkdir -p $c
        cp *.java $c

    - name: Java
      run: |
        javac -g -d Classes -cp Classes $c/*.java
END

  for my $j(@j)                                                                 # Java files
   {$y .= <<END;

    - name: Test $j
      if: matrix.task == '$j'
      run: |
        java -cp Classes $c/$j

END
   }

  my $f = writeFileUsingSavedToken $user, $repo, $wf, $y;                       # Upload workflow
  lll "$f  Ubuntu work flow for $repo";
 }
else
 {say STDERR "No Java files changed";
 }
