//------------------------------------------------------------------------------
// Stuck
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

class Stuck extends Test                                                        // A fixed size collection of key, data pairs
 {final int size;                                                               // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout layout;                                                          // Layout of the stuck
  final Layout.Field stuckSize;                                                 // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field

//D1 Construction                                                               // Create a stuck

  Stuck(int Size, int BitsPerKey, int BitsPerData)                              // Create the stuck. The memory layout containing the stuck
   {size        = Size;                                                         // The maximum number of entries in the stuck.
    bitsPerKey  = BitsPerKey;                                                   // The number of bits needed to define a key
    bitsPerData = BitsPerData;                                                  // The number of bits needed to define a data field
    layout      = layout();
    stuckSize   = layout.locateFieldByName("stuckSize");                        // Current size of stuck up to the maximum size
    stuckKeys   = layout.locateFieldByName("stuckKeys");                        // Keys field
    stuckData   = layout.locateFieldByName("stuckData");                        // Data field
   }

  Layout layout()                                                               // Layout describing stuck
   {return new Layout(String.format("""
stuckSize      var    %d
Stuck          array  %d
  stuck        struct
    stuckKeys  var    %d
    stuckData  var    %d
""", logTwo(size), size, bitsPerKey, bitsPerData));
   }

  static void test_parse()                                                      // Parse the stuck
   {final Stuck a = new Stuck(4, 4, 4);
    stop(a.layout);
   }

//D1 Actions                                                                    // Actions on the stuck

  void push                                                                     // Push a new key, data pair on the stack
   (Layout.Field Key,
    Layout.Field Data)
   {if
   }

//D1 Tests                                                                      // tests

  static void oldTests()                                                        // Tests thought to be in good shape
   {
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_parse();
   }

  public static void main(String[] args)                                        // Test if called as a program
   {try                                                                         // Get a traceback in a format clickable in Geany if something goes wrong to speed up debugging.
     {if (github_actions) oldTests(); else newTests();                          // Tests to run
      if (github_actions)                                                       // Coverage analysis
       {coverageAnalysis(sourceFileName(), 12);
       }
      testSummary();                                                            // Summarize test results
      System.exit(testsFailed);
     }
    catch(Exception e)                                                          // Get a traceback in a format clickable in Geany
     {System.err.println(e);
      System.err.println(fullTraceBack(e));
      System.exit(1);
     }
   }
 }
