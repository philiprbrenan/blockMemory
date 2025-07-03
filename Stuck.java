//------------------------------------------------------------------------------
// Stuck
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

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
stuckSize    var    %d
Stuck        array  %d
  stuckKeys  var    %d
  stuckData  var    %d
""", logTwo(size), size, bitsPerKey, bitsPerData));
   }

  static void test_parse()                                                      // Parse the stuck
   {final Stuck a = new Stuck(4, 4, 4);

    //stop(a.L);
    ok(a.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           0  var        2
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         0  var        4   Stuck                        4
  3       2    stuckData         0  var        4   Stuck                        4
""");
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
         {L.stopProgram("Cannot pop an empty stuck");
          return;
         }
        stuckSize.iDec();
        stuckKeys.iRead(stuckSize);
        stuckData.iRead(stuckSize);
        L.P.pc++;
       }
     };
   }

  void unshift()                                                                // Unshift a key, data pair into the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {push();
          return;
         }
        if (stuckSize.value >= size)
         {L.stopProgram("Cannot unshift into a full stuck");
          return;
         }

        for (int i = size; i > 1; --i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i-2].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i-2].clone();
         }
        stuckKeys.setBitsFromInt(stuckKeys.memory[0], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[0], stuckData.value);

        stuckSize.iInc();
        L.P.pc++;
       }
     };
   }

  void shift()                                                                  // Shift a key, data pair from the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot shift an empty stuck");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[0]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[0]);

        for (int i = 1; i < stuckSize.value; ++i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i].clone();
         }

        stuckSize.iDec();
        L.P.pc++;
       }
     };
   }

  void elementAt(Layout.Field index)                                            // Get the key, data pair at the specified index
   {L.P.new Instruction()
     {void action()
       {if (index.value < 0)
         {L.stopProgram("Index must be greater than zero");
          return;
         }
        if (index.value >= stuckSize.value)
         {L.stopProgram("Cannot get element beyond end of stuck");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[index.value]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[index.value]);
        L.P.pc++;
       }
     };
   }

  void setElementAt(Layout.Field index)                                         // Set the key, data pair at the specified index
   {L.P.new Instruction()
     {void action()
       {if (index.value < 0)
         {L.stopProgram("Index must be greater than zero");
          return;
         }

        if (index.value >= size)
         {L.stopProgram("Cannot set element beyond actual end of stuck");
          return;
         }

        if (index.value > stuckSize.value)
         {L.stopProgram("Cannot set element more than one element beyond current end of stuck");
          return;
         }

        if (index.value == stuckSize.value)
         {push();
          return;
         }

        stuckKeys.iWrite(stuckKeys, index);
        stuckData.iWrite(stuckData, index);
        L.P.pc++;
       }
     };
   }

  void insertElementAt(Layout.Field Index)                                      // Insert a key data pair at thespecified index moving the elements abiove this position up one place to make room
   {L.P.new Instruction()
     {void action()
       {if (Index.value < 0)
         {L.stopProgram("Index must be greater than zero");
          return;
         }
        if (stuckSize.value == Index.value)
         {push();
          return;
         }
        if (stuckSize.value >= size)
         {L.stopProgram("Cannot insert into a full stuck");
          return;
         }

        for (int i = size; i > Index.value+1; --i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i-2].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i-2].clone();
         }
        stuckKeys.setBitsFromInt(stuckKeys.memory[Index.value], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[Index.value], stuckData.value);

        stuckSize.iInc();
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
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=2");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=6, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=1");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=4, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=0");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=6, 3=8");

    s.L.P.supressErrorMessagePrint = true;
    s.L.P.clearProgram(); s.pop(); s.L.runProgram();
    //stop(s.L.P.rc);
  ok(s.L.P.rc, "Cannot pop an empty stuck");

    return s;
   }

  protected static Stuck test_unshift()
   {final Stuck  s = test_push();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(9); d.iWrite(11); s.unshift(); s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=9, 0=9, 1=1, 2=2, 3=3");
    ok(s.stuckData, "stuckData: value=11, 0=11, 1=2, 2=4, 3=6");

    s.L.P.supressErrorMessagePrint = true;
    s.L.clearProgram(); k.iWrite(9); d.iWrite(11); s.unshift(); s.L.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot unshift into a full stuck");

    return s;
   }

  protected static Stuck test_shift()
   {final Stuck s = test_push();

    s.L.clearProgram(); s.shift(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=2, 1=3, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=4, 1=6, 2=8, 3=8");

    s.L.clearProgram(); s.shift(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=2");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=3, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=4, 0=6, 1=8, 2=8, 3=8");

    s.L.clearProgram(); s.shift(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=1");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=4, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=6, 0=8, 1=8, 2=8, 3=8");

    s.L.clearProgram(); s.shift(); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=0");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=4, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=8, 1=8, 2=8, 3=8");

    s.L.P.supressErrorMessagePrint = true;
    s.L.P.clearProgram(); s.shift(); s.L.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot shift an empty stuck");

    return s;
   }

  protected static void test_elementAt()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
index var 4
""");

    Layout.Field index = l.locateFieldByName("index");

    s.L.clearProgram(); index.iWrite(2); s.elementAt(index); s.L.runProgram();
    //stop(s.L);
    ok(s.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           4  var        2
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         3  var        4   Stuck                        4
  3       2    stuckData         6  var        4   Stuck                        4
""");

    s.L.clearProgram(); index.iWrite(1); s.elementAt(index); s.L.runProgram();
    //stop(s.L);
    ok(s.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           4  var        2
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         2  var        4   Stuck                        4
  3       2    stuckData         4  var        4   Stuck                        4
""");

    s.L.P.supressErrorMessagePrint = true;
    s.L.clearProgram(); index.iWrite(5); s.elementAt(index); s.L.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot get element beyond end of stuck");
   }

  protected static void test_setElementAt()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
index var 4
""");

    Layout.Field index = l.locateFieldByName("index");

    ok(s.stuckKeys, "stuckKeys: value=5, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=10, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    index.iWrite(1);
    s.stuckKeys.iWrite(9);
    s.stuckData.iWrite(11);
    s.setElementAt(index);
    s.L.runProgram();

    ok(s.stuckKeys, "stuckKeys: value=9, 0=1, 1=9, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=11, 0=2, 1=11, 2=6, 3=8");
   }

  protected static void test_insertElementAt()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
index var 4
""");

    Layout.Field index = l.locateFieldByName("index");
    s.L.clearProgram(); s.pop(); s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); index.iWrite(1); s.stuckKeys.iWrite(9);  s.stuckData.iWrite(9); s.insertElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=9, 0=1, 1=9, 2=2, 3=3");
    ok(s.stuckData, "stuckData: value=9, 0=2, 1=9, 2=4, 3=6");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    s.L.clearProgram(); index.iWrite(1); s.stuckKeys.iWrite(10);  s.stuckData.iWrite(12); s.insertElementAt(index); s.L.runProgram();
    ok(s.stuckKeys, "stuckKeys: value=10, 0=1, 1=10, 2=9, 3=2");
    ok(s.stuckData, "stuckData: value=12, 0=2, 1=12, 2=9, 3=4");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    s.L.clearProgram(); index.iWrite(0); s.stuckKeys.iWrite(11);  s.stuckData.iWrite(13); s.insertElementAt(index); s.L.runProgram();
    ok(s.stuckKeys, "stuckKeys: value=11, 0=11, 1=1, 2=10, 3=9");
    ok(s.stuckData, "stuckData: value=13, 0=13, 1=2, 2=12, 3=9");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_parse();
    test_push();
    test_pop();
    test_unshift();
    test_shift();
    test_elementAt();
    test_setElementAt();
    test_insertElementAt();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
    test_insertElementAt();
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
