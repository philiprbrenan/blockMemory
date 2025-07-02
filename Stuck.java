//------------------------------------------------------------------------------
// Stuck
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

class Stuck extends Test                                                        // A fixed size collection of key, data pairs
 {final int size;                                                               // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field stuckSize;                                                 // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field

//D1 Construction                                                               // Create a stuck

  Stuck(int Size, int BitsPerKey, int BitsPerData)                              // Create the stuck. The memory layout containing the stuck
   {size        = Size;                                                         // The maximum number of entries in the stuck.
    bitsPerKey  = BitsPerKey;                                                   // The number of bits needed to define a key
    bitsPerData = BitsPerData;                                                  // The number of bits needed to define a data field
    L           = layout();
    stuckSize   = L.locateFieldByName("stuckSize");                             // Current size of stuck up to the maximum size
    stuckKeys   = L.locateFieldByName("stuckKeys");                             // Keys field
    stuckData   = L.locateFieldByName("stuckData");                             // Data field
   }

  Layout layout()                                                               // Layout describing stuck, Having the keys ordered sequqntiall makes it easy to compare them in parallel and find the first key greater than or equal to a search key, this being the most common operation.
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
    stop(a.L);
   }

//D1 Actions                                                                    // Actions on the stuck

  void push                                                                     // Push a new key, data pair on the stack
   (Layout.Field Key,
    Layout.Field Data)
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size) L.stopProgram("Cannot push to a full stuck");
        stuckKeys.iWrite(Key , stuckSize);
        stuckData.iWrite(Data, stuckSize);
        stuckSize.iInc();
        L.P.pc++;
       }
     };
   }

//D1 Tests                                                                      // Tests

  protected static Stuck testStuck()                                            // Create a test stuck
   {return new Stuck(4, 4, 4);
   }

  protected static void test_push()
   {final Stuck  s = testStuck();
    final Layout l = s.L.additionalLayout("""
key  var 4
data var 4
""");

    Layout.Field k = l.locateFieldByName("key");
    Layout.Field d = l.locateFieldByName("data");

    l.clearProgram(); k.iWrite(1); d.iWrite(2); s.push(k, d); l.runProgram();
    l.clearProgram(); k.iWrite(2); d.iWrite(4); s.push(k, d); l.runProgram();
    l.clearProgram(); k.iWrite(3); d.iWrite(6); s.push(k, d); l.runProgram();
    l.clearProgram(); k.iWrite(4); d.iWrite(8); s.push(k, d); l.runProgram();
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_parse();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_push();
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
