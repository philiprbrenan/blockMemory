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
        L.P.pc++;
       }
     };
    stuckKeys.iWrite(stuckKeys, stuckSize);
    stuckData.iWrite(stuckData, stuckSize);
    stuckSize.iInc();
   }

  void pop()                                                                    // Pop a key, data pair from the stack
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot pop an empty stuck");
          return;
         }
        L.P.pc++;
       }
     };
    stuckSize.iDec();
    stuckKeys.iRead(stuckSize);
    stuckData.iRead(stuckSize);
   }

  void unshift()                                                                // Unshift a key, data pair into the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size)
         {L.stopProgram("Cannot unshift into a full stuck");
          return;
         }

        for (int i = size; i > 1; --i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i-2].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i-2].clone();
         }
        stuckKeys.setBitsFromInt(stuckKeys.memory[0], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[0], stuckData.value);
        L.P.pc++;
       }
     };
    stuckSize.iInc();
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

        L.P.pc++;
       }
     };
    stuckSize.iDec();
   }

  void firstElement()                                                           // Get the first key, data pair
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot get the first element because the stuck is empty");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[0]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[0]);
        L.P.pc++;
       }
     };
   }

  void lastElement()                                                            // Get the last key, data pair
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot get the last element because the stuck is empty");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[stuckSize.value-1]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[stuckSize.value-1]);
        L.P.pc++;
       }
     };
   }

  void pastLastElement()                                                        // Get the key, data pair beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value > size-1)
         {L.stopProgram("Cannot get the element beyond the last element because the stuck is full");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[stuckSize.value]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[stuckSize.value]);
        L.P.pc++;
       }
     };
   }

  void elementAt(Layout.Field index)                                            // Get the key, data pair at the specified index
   {L.P.new Instruction()
     {void action()
       {if (index.value >= stuckSize.value)
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
       {if (index.value > stuckSize.value)
         {L.stopProgram("Cannot set element more than one step beyond current end of stuck");
          return;
         }
        if (index.value == stuckSize.value) stuckSize.value++;                  // Extending the stuck
        L.P.pc++;
       }
     };
    stuckKeys.iWrite(stuckKeys, index);
    stuckData.iWrite(stuckData, index);
   }

  void setFirstElement()                                                        // Get the first key, data pair
   {L.P.new Instruction()
     {void action()
       {stuckKeys.setBitsFromInt(stuckKeys.memory[0], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[0], stuckData.value);
        if (stuckSize.value == 0) stuckSize.value++;
        L.P.pc++;
       }
     };
   }

  void setLastElement()                                                         // Get the last key, data pair
   {L.P.new Instruction()
     {void action()
       {stuckKeys.setBitsFromInt(stuckKeys.memory[stuckSize.value-1], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[stuckSize.value-1], stuckData.value);
        if (stuckSize.value == 0) stuckSize.value++;
        L.P.pc++;
       }
     };
   }

  void setPastLastElement()                                                     // Get the key, data pair beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size)
         {L.stopProgram("Cannot get the element beyond the last element because the stuck is full");
          return;
         }

        stuckKeys.setBitsFromInt(stuckKeys.memory[stuckSize.value], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[stuckSize.value], stuckData.value);
        stuckSize.value++;
        L.P.pc++;
       }
     };
   }

  void insertElementAt(Layout.Field Index)                                      // Insert a key, data pair at the specified index moving the elements above this position up one place to make room
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= size)
         {L.stopProgram("Cannot insert into a full stuck");
          return;
         }

        for (int i = size; i > Index.value+1; --i)
         {stuckKeys.memory[i-1] = (BitSet)stuckKeys.memory[i-2].clone();
          stuckData.memory[i-1] = (BitSet)stuckData.memory[i-2].clone();
         }

        stuckKeys.setBitsFromInt(stuckKeys.memory[Index.value], stuckKeys.value);
        stuckData.setBitsFromInt(stuckData.memory[Index.value], stuckData.value);

        L.P.pc++;
       }
     };
    stuckSize.iInc();
   }

  void removeElementAt(Layout.Field Index)                                      // Get the value of the indexed key, data pair at the specified index moving the elements above down into this position
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value == 0)
         {L.stopProgram("Cannot remove element from empty stuck");
          return;
         }
        if (Index.value >= stuckSize.value)
         {L.stopProgram("Cannot remove element beyond end of actual stuck");
          return;
         }

        stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[Index.value]);
        stuckData.value = stuckData.getIntFromBits(stuckData.memory[Index.value]);

        for (int i = Index.value; i < stuckSize.value-1; ++i)
         {stuckKeys.memory[i] = (BitSet)stuckKeys.memory[i+1].clone();
          stuckData.memory[i] = (BitSet)stuckData.memory[i+1].clone();
         }

        L.P.pc++;
       }
     };
    stuckSize.iDec();
   }

  void search_eq(Layout.Field Found, Layout.Field Index)                        // Search for an equal key.
   {L.P.new Instruction()
     {void action()
       {L.P.pc++;
        for (int i = 0; i < stuckSize.value; ++i)                               // Check each key
         {final int v = stuckKeys.getIntFromBits(stuckKeys.memory[i]);
          if (stuckKeys.value == v)
           {Found.value = 1; Index.value = i;
            stuckData.value = stuckData.getIntFromBits(stuckData.memory[i]);
            return;
           }
         }
        Found.value = 0;
       }
     };
   }

  void search_le(Layout.Field Found, Layout.Field Index)                        // Search for the first key.
   {L.P.new Instruction()
     {void action()
       {L.P.pc++;
        for (int i = 0; i < stuckSize.value; ++i)                               // Check each key
         {final int v = stuckKeys.getIntFromBits(stuckKeys.memory[i]);
          if (stuckKeys.value <= v)
           {Found.value = 1; Index.value = i;
            stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[i]);
            stuckData.value = stuckData.getIntFromBits(stuckData.memory[i]);
            return;
           }
         }
        Found.value = 0;
       }
     };
   }

  void concatenate(Stuck source)                                                // Concatenate the indicated stuck on to the end one
   {L.P.new Instruction()
     {void action()
       {L.P.pc++;
        final int sourceSize = source.stuckSize.value;
        final int targetSize =        stuckSize.value;
        if (sourceSize + targetSize > size)
         {L.P.stopProgram("Not enough room in target to concatenate source as well");
          return;
         }
        for (int i = 0; i < sourceSize; ++i)                                    // Concatenate each key, data pair
         {stuckKeys.memory[targetSize+i] = (BitSet)source.stuckKeys.memory[i].clone();
          stuckData.memory[targetSize+i] = (BitSet)source.stuckData.memory[i].clone();
         }
        stuckSize.value += sourceSize;                                          // New size of target
        L.P.pc++;
       }
     };
   }

  void splitIntoTwo(Stuck Left, Stuck Right, Layout.Field Copy)                 // Copy the first key, data pairs into the left stuck, the remainder into the right stuck.  The original source stuck is not modifiedr
   {L.P.new Instruction()
     {void action()
       {L.P.pc++;
        if (Copy.value >= stuckSize.value)
         {L.P.stopProgram("Cannot copy beyond end of stuck");
          return;
         }
        if (Left.size  < Copy.value)
         {L.P.stopProgram("Left stuck too small");
          return;
         }
        if (Right.size < stuckSize.value - Copy.value)
         {L.P.stopProgram("Right stuck too small");
          return;
         }

        for (int i = 0; i < Copy.value; ++i)                                    // Copy to left
         {Left.stuckKeys.memory[i] = (BitSet)stuckKeys.memory[i].clone();
          Left.stuckData.memory[i] = (BitSet)stuckData.memory[i].clone();
         }
        Left.stuckSize.value = Copy.value;                                      // New size of left

        for (int i = 0; i < stuckSize.value - Copy.value; ++i)                  // Copy to right
         {Right.stuckKeys.memory[i] = (BitSet)stuckKeys.memory[Copy.value + i].clone();
          Right.stuckData.memory[i] = (BitSet)stuckData.memory[Copy.value + i].clone();
         }
        Right.stuckSize.value = stuckSize.value - Copy.value;                   // New size of right
       }
     };
   }

  void splitIntoThree(Stuck Left, Stuck Right, Layout.Field Copy,               // Copy the first key, data pairs into the left stuck, insert the next the pair  into the parent at the indicated position and copy the reminder to the right stuck
    Stuck Parent, Layout.Field At)
   {L.P.new Instruction()
     {void action()
       {L.P.pc++;
        if (Copy.value >= stuckSize.value)
         {L.P.stopProgram("Cannot copy beyond end of stuck");
          return;
         }
        if (Left.size  <  Copy.value)
         {L.P.stopProgram("Left stuck too small");
          return;
         }
        if (Right.size <  stuckSize.value - Copy.value -1)
         {L.P.stopProgram("Right stuck too small");
          return;
         }
        if (At.value   >  Parent.stuckSize.value)
         {L.P.stopProgram("At is too big for parent");
          return;
         }

        for (int i = 0; i < Copy.value; ++i)                                    // Copy to left
         {Left.stuckKeys.memory[i] = (BitSet)stuckKeys.memory[i].clone();
          Left.stuckData.memory[i] = (BitSet)stuckData.memory[i].clone();
         }
        Left.stuckSize.value = Copy.value;                                      // New size of left

        for (int i = 0; i < stuckSize.value - Copy.value - 1; ++i)              // Copy to right
         {Right.stuckKeys.memory[i] = (BitSet)stuckKeys.memory[Copy.value + i + 1].clone();
          Right.stuckData.memory[i] = (BitSet)stuckData.memory[Copy.value + i + 1].clone();
         }
        Right.stuckSize.value  = stuckSize.value - Copy.value - 1;              // New size of right
        Parent.stuckKeys.value = stuckKeys.getIntFromBits(stuckKeys.memory[Copy.value]);
        Parent.stuckData.value = stuckData.getIntFromBits(stuckData.memory[Copy.value]);
       }
     };

    Parent.insertElementAt(At);
   }

//D1 Tests                                                                      // Tests

  protected static Stuck testStuck()                                            // Create a test stuck
   {return new Stuck(4, 4, 4);
   }

  protected static Stuck testSmallStuck()                                       // Create a test stuck
   {return new Stuck(2, 4, 4);
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

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    k.iWrite(5); d.iWrite(10); s.push();
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot push to a full stuck");

    s.L.clearProgram(); k.iWrite(0); d.iWrite(0); s.L.runProgram();             // Clean up key and data value

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

    s.L.P.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.pop();
    s.L.runProgram();
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

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    k.iWrite(9); d.iWrite(11); s.unshift();
    s.L.runProgram();
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

    s.L.P.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.shift();
    s.L.runProgram();
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

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(5); s.elementAt(index);
    s.L.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot get element beyond end of stuck");
   }

  protected static void test_setElementAt()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
index var 4
""");

    Layout.Field index = l.locateFieldByName("index");

    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    index.iWrite(1);
    s.stuckKeys.iWrite(9);
    s.stuckData.iWrite(11);
    s.setElementAt(index);
    s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=9, 0=1, 1=9, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=11, 0=2, 1=11, 2=6, 3=8");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.pop();
    s.pop();
    index.iWrite(3);
    s.setElementAt(index);
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot set element more than one step beyond current end of stuck");

    s.L.clearProgram();
    index.iWrite(2);
    s.stuckKeys.iWrite(8);
    s.stuckData.iWrite(12);
    s.setElementAt(index);
    s.L.runProgram();

    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=8, 0=1, 1=9, 2=8, 3=4");
    ok(s.stuckData, "stuckData: value=12, 0=2, 1=11, 2=12, 3=8");
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
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=10, 0=1, 1=10, 2=9, 3=2");
    ok(s.stuckData, "stuckData: value=12, 0=2, 1=12, 2=9, 3=4");

    s.L.clearProgram(); s.pop(); s.L.runProgram();
    s.L.clearProgram(); index.iWrite(0); s.stuckKeys.iWrite(11);  s.stuckData.iWrite(13); s.insertElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=11, 0=11, 1=1, 2=10, 3=9");
    ok(s.stuckData, "stuckData: value=13, 0=13, 1=2, 2=12, 3=9");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(0); s.stuckKeys.iWrite(12);  s.stuckData.iWrite(14); s.insertElementAt(index);
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot insert into a full stuck");
   }

  protected static void test_removeElementAt()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
index var 4
""");

    Layout.Field index = l.locateFieldByName("index");

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram(); index.iWrite(1); s.removeElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=1, 1=3, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=4, 0=2, 1=6, 2=8, 3=8");

    s.L.clearProgram(); index.iWrite(1); s.removeElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=2");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=6, 0=2, 1=8, 2=8, 3=8");

    s.L.clearProgram(); index.iWrite(1); s.removeElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=1");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=8, 2=8, 3=8");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(1); s.removeElementAt(index);
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot remove element beyond end of actual stuck");

    s.L.clearProgram(); index.iWrite(0); s.removeElementAt(index); s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=0");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=1, 1=4, 2=4, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=8, 2=8, 3=8");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(0); s.removeElementAt(index);
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot remove element from empty stuck");
   }

  protected static void test_search_eq()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
found bit
index var 4
""");

    Layout.Field found = l.locateFieldByName("found");
    Layout.Field index = l.locateFieldByName("index");

    s.L.clearProgram(); s.stuckKeys.iWrite(2); s.search_eq(found, index); s.L.runProgram();
    ok(found.value, 1);
    ok(index.value, 1);
    ok(s.stuckKeys.value, 2);
    ok(s.stuckData.value, 4);

    s.L.clearProgram(); s.stuckKeys.iWrite(5); s.search_eq(found, index); s.L.runProgram();
    ok(found.value, 0);
   }

  protected static void test_search_le()
   {final Stuck  s = testStuck();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.L.clearProgram(); k.iWrite(2); d.iWrite(3); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(4); d.iWrite(5); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(6); d.iWrite(7); s.push(); s.L.runProgram();
    s.L.clearProgram(); k.iWrite(8); d.iWrite(9); s.push(); s.L.runProgram();

    final Layout l = s.L.additionalLayout("""
found bit
index var 4
""");

    Layout.Field found = l.locateFieldByName("found");
    Layout.Field index = l.locateFieldByName("index");

    s.L.clearProgram(); s.stuckKeys.iWrite(2); s.search_le(found, index); s.L.runProgram();
    ok(found.value, 1);
    ok(index.value, 0);
    ok(s.stuckKeys.value, 2);
    ok(s.stuckData.value, 3);

    s.L.clearProgram(); s.stuckKeys.iWrite(3); s.search_le(found, index); s.L.runProgram();
    ok(found.value, 1);
    ok(index.value, 1);
    ok(s.stuckKeys.value, 4);
    ok(s.stuckData.value, 5);

    s.L.clearProgram(); s.stuckKeys.iWrite(10); s.search_le(found, index); s.L.runProgram();
    ok(found.value, 0);
   }

  protected static void test_concatenate()
   {final Stuck s = test_push();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");
    final Stuck t = test_push(); t.L.P = s.L.P;
    ok(t.stuckSize, "stuckSize: value=4");
    ok(t.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(t.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.concatenate(t);
    s.L.runProgram();
    ok(s.L.P.rc, "Not enough room in target to concatenate source as well");

    s.L.clearProgram();
    s.pop();
    s.pop();
    t.pop();
    t.pop();
    s.concatenate(t);
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=2, 2=1, 3=2");
    ok(s.stuckData, "stuckData: value=6, 0=2, 1=4, 2=2, 3=4");
   }

  protected static void test_splitIntoTwo()
   {final Stuck s = test_push();
    final Stuck S = testSmallStuck(); S.L.P = s.L.P;

    final Layout l = s.L.additionalLayout("""
count var 4
""");

    Layout.Field count = l.locateFieldByName("count");

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    final Stuck L = test_push(); L.L.P = s.L.P;
    final Stuck R = test_push(); R.L.P = s.L.P;

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    count.iWrite(6);
    s.splitIntoTwo(L, R, count);
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot copy beyond end of stuck");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    count.iWrite(3);
    s.splitIntoTwo(S, R, count);
    s.L.runProgram();
    ok(s.L.P.rc, "Left stuck too small");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    count.iWrite(1);
    s.splitIntoTwo(L, S, count);
    s.L.runProgram();
    ok(s.L.P.rc, "Right stuck too small");

    s.L.clearProgram();
    count.iWrite(2);
    s.splitIntoTwo(L, R, count);
    s.L.runProgram();

    ok(L.stuckSize, "stuckSize: value=2");
    ok(L.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(L.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");
    ok(R.stuckSize, "stuckSize: value=2");
    ok(R.stuckKeys, "stuckKeys: value=0, 0=3, 1=4, 2=3, 3=4");
    ok(R.stuckData, "stuckData: value=0, 0=6, 1=8, 2=6, 3=8");
   }

  protected static void test_splitIntoThree()
   {final Stuck s = test_push();

    final Layout l = s.L.additionalLayout("""
at    var 4
count var 4
""");

    Layout.Field at    = l.locateFieldByName("at");
    Layout.Field count = l.locateFieldByName("count");

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    final Stuck P = test_push(); P.L.P = s.L.P;
    final Stuck L = test_push(); L.L.P = s.L.P;
    final Stuck R = test_push(); R.L.P = s.L.P;

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    at.iWrite(11);
    count.iWrite(2);
    s.splitIntoThree(L, R, count, P, at);
    s.L.runProgram();
    ok(s.L.P.rc, "At is too big for parent");

    s.L.clearProgram();
    at.iWrite(1);
    count.iWrite(2);
    P.pop();
    s.splitIntoThree(L, R, count, P, at);
    s.L.runProgram();

    ok(L.stuckSize, "stuckSize: value=2");
    ok(L.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(L.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");
    ok(R.stuckSize, "stuckSize: value=1");
    ok(R.stuckKeys, "stuckKeys: value=0, 0=4, 1=2, 2=3, 3=4");
    ok(R.stuckData, "stuckData: value=0, 0=8, 1=4, 2=6, 3=8");
    ok(P.stuckSize, "stuckSize: value=4");
    ok(P.stuckKeys, "stuckKeys: value=3, 0=1, 1=3, 2=2, 3=3");
    ok(P.stuckData, "stuckData: value=6, 0=2, 1=6, 2=4, 3=6");
   }

  protected static void test_firstLastPast()
   {final Stuck s = test_push();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.firstElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.pop();
    s.lastElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=6, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.pastLastElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=8, 0=2, 1=4, 2=6, 3=8");
   }

  protected static void test_setFirstLastPast()
   {final Stuck s = test_push();

    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=0, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setFirstElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=2, 1=2, 2=3, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=6, 3=8");

    s.L.clearProgram();
    s.pop();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setLastElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=3");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=2, 1=2, 2=2, 3=4");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=2, 3=8");

    s.L.clearProgram();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setPastLastElement();
    s.L.runProgram();
    ok(s.stuckSize, "stuckSize: value=4");
    ok(s.stuckKeys, "stuckKeys: value=2, 0=2, 1=2, 2=2, 3=2");
    ok(s.stuckData, "stuckData: value=2, 0=2, 1=4, 2=2, 3=2");

    s.L.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setPastLastElement();
    s.L.runProgram();
    ok(s.L.P.rc, "Cannot get the element beyond the last element because the stuck is full");
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
    test_removeElementAt();
    test_search_eq();
    test_search_le();
    test_concatenate();
    test_splitIntoTwo();
    test_splitIntoThree();
    test_firstLastPast();
    test_setFirstLastPast();
   }

  static void newTests()                                                        // Tests being worked on
   {oldTests();
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
