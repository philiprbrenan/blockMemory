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

  void push()                                                                   // Push a new key, data pair on the stack
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size)
         {L.stopProgram("Cannot push to a full stuck");
          return;
         }
        stuckKeys.iWrite(stuckKeys, stuckSize);
        stuckData.iWrite(stuckData, stuckSize);
        stuckSize.iInc();
        L.P.pc++;
       }
     };
   }

  void pop()                                                                    // Pop a key, data pair from the stack
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot pop empty stack");
          return;
         }
        stuckSize.iDec();
        stuckKeys.iRead(stuckSize);
        stuckData.iRead(stuckSize);
        L.P.pc++;
       }
     };
   }

//D1 Tests                                                                      // Tests

  protected static Stuck testStuck()                                            // Create a test stuck
   {return new Stuck(4, 4, 4);
   }

  protected static Stuck test_push()
   {final Stuck  s = testStuck();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.L.clearProgram(); k.iWrite(1); d.iWrite(2); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(2); d.iWrite(4); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(3); d.iWrite(6); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(4); d.iWrite(8); s.push(); s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");

    s.L.P.supressErrorMessagePrint = true;
    s.L.clearProgram(); k.iWrite(5); d.iWrite(10); s.push(); s.L.runProgram();
    ok(s.L.P.rc, "Cannot push to a full stuck");

    return s;
   }

  protected static Stuck test_pop()
   {final Stuck  s = test_push();

    s.L.clearProgram();
    s.pop();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
//    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
//    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");
//
//    s.L.clearProgram(); s.pop(); s.L.runProgram();
//    ok(s.stuckSize, "stuckSize: value=2");
//    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4");
//    ok(s.stuckData, "stuckData: value=6, 0=2, 1=4, 2=6, 3=8");
//
//    s.L.clearProgram(); s.pop(); s.L.runProgram();
//    ok(s.stuckSize, "stuckSize: value=1");
//    ok(s.stuckKeys, "stuckKeys: value=2, 0=1, 1=2, 2=3, 3=4");
//    ok(s.stuckData, "stuckData: value=4, 0=2, 1=4, 2=6, 3=8");
//
//    s.L.clearProgram(); s.pop(); s.L.runProgram();
//    ok(s.stuckSize, "stuckSize: value=0");
//    ok(s.stuckKeys, "stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4");
//    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=6, 3=8");
//
//    s.L.P.supressErrorMessagePrint = true;
//    s.L.P.clearProgram(); s.pop(); s.L.runProgram();
//    stop(s.L.P.rc);
    return s;
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_parse();
    test_push();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_pop();
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
