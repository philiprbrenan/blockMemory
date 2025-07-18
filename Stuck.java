//------------------------------------------------------------------------------
// Stuck
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2024
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Stuck extends Test                                                        // A fixed size collection of key, data pairs
 {final int maxStuckSize;                                                       // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field stuckSize;                                                 // Current size of stuck up to the maximum size
  final Layout.Field stuckKeys;                                                 // Keys field
  final Layout.Field stuckData;                                                 // Data field

//D1 Construction                                                               // Create a stuck

  Stuck(int MaxStuckSize, int BitsPerKey, int BitsPerData)                      // Create the stuck. The memory layout containing the stuck
   {maxStuckSize = MaxStuckSize;                                                // The maximum number of entries in the stuck.
    bitsPerKey   = BitsPerKey;                                                  // The number of bits needed to define a key
    bitsPerData  = BitsPerData;                                                 // The number of bits needed to define a data field
    L            = layout();
    stuckSize    = L.locateFieldByName("stuckSize");                            // Current size of stuck up to the maximum size
    stuckKeys    = L.locateFieldByName("stuckKeys");                            // Keys field
    stuckData    = L.locateFieldByName("stuckData");                            // Data field
   }

  Layout layout()                                                               // Layout describing stuck, Having the keys ordered sequqntiall makes it easy to compare them in parallel and find the first key greater than or equal to a search key, this being the most common operation.
   {return new Layout(String.format("""
stuckSize    var    %d
Stuck        array  %d
  stuckKeys  var    %d
  stuckData  var    %d
""", logTwo(maxStuckSize)+1, maxStuckSize, bitsPerKey, bitsPerData));
   }

  Stuck duplicate()                                                             // Duplicate this struck
   {final Stuck s = new Stuck(maxStuckSize, bitsPerKey, bitsPerData);
    s.copy(this);
    s.L.P = L.P;
    return s;
   }

  void copy(Stuck Source)                                                       // Copy one stuck into another
   {if (maxStuckSize != Source.maxStuckSize)
     {L.P.stopProgram("Size mismatch");
      return;
     }
    if (bitsPerKey != Source.bitsPerKey)
     {L.P.stopProgram("Bits per key mismatch");
      return;
     }
    if (bitsPerData != Source.bitsPerData)
     {L.P.stopProgram("Bits per data mismatch");
      return;
     }
    stuckSize.value = Source.stuckSize.value;
    stuckKeys.value = Source.stuckKeys.value;
    stuckData.value = Source.stuckData.value;
    for (int i = 0; i < maxStuckSize; i++)
     {stuckKeys.move(i, Source.stuckKeys, i);
      stuckData.move(i, Source.stuckData, i);
     }
   }

  Layout.Field variable(String name, int size)                                  // Create a variable
   {final Layout.Field v = L.variable(name, size);
    return v;
   }

  Layout.Field found()  {return variable("found",      1);}                     // Whether a key was found in a stuck or not
  Layout.Field empty()  {return variable("empty",      1);}                     // Whether the stuck is empty
  Layout.Field full()   {return variable("full",       1);}                     // Whether the stuck is full
  Layout.Field success(){return variable("success",    1);}                     // Whether a merge completed successfully
  Layout.Field index()  {return variable("stuckIndex", logTwo(maxStuckSize)+1);}// Index a key, data pair in a stuck
  Layout.Field count()  {return variable("count",      logTwo(maxStuckSize)+1);}// Number of key, data pairs to copy
  Layout.Field at()     {return variable("at",         logTwo(maxStuckSize)+1);}// Position in which to insert in parent
  Layout.Field key()    {return variable("key",        bitsPerKey);}            // A field capable of holding a key value
  Layout.Field data()   {return variable("data",       bitsPerData);}           // A field capable of holding a data value
  Layout.Field fullButOne() {return variable("fullButOne", 1);}                 // Whether the stuck is full except for one

  Layout.Field key(int Value)
   {final Layout.Field k = key();
    k.value = Value;
    return k;
   }

  void runProgram  () {L.runProgram();}                                         // Runb the program
  void clearProgram() {L.clearProgram();}                                       // Clear the current program

//D1 Attributes                                                                 // Answers to questions about the stuck

  void isEmpty(Layout.Field empty)                                              // Whether the stuck is empty
   {empty.value = !stuckSize.asBoolean() ? 1 : 0;
   }

  void iIsEmpty(Layout.Field empty)                                             // Whether the stuck is empty
   {L.P.new Instruction()
     {void action()
       {isEmpty(empty);
       }
     };
   }

  void isFull(Layout.Field full)                                                // Whether the stuck is full
   {full.value = stuckSize.value >= maxStuckSize ? 1 : 0;
   }

  void iIsFull(Layout.Field full)                                               // Whether the stuck is full
   {L.P.new Instruction()
     {void action()
       {isFull(full);
       }
     };
   }

  void isFullButOne(Layout.Field fullButOne)                                    // Whether the stuck is full except for one
   {fullButOne.value = stuckSize.value >= maxStuckSize - 1 ? 1 : 0;
   }

  void iIsFullButOne(Layout.Field fullButOne)                                   // Whether the stuck is full except for one
   {L.P.new Instruction()
     {void action()
       {isFullButOne(fullButOne);
       }
     };
   }

//D1 Print                                                                      // Print  the stcuk

  public String toString()
   {final StringBuilder s = new StringBuilder();
    s.append(stuckSize+"\n");
    s.append(stuckKeys+"\n");
    s.append(stuckData+"\n");
    return ""+s;
   }

  public String toTest()
   {final StringBuilder s = new StringBuilder();
    return "ok(s, \"\"\"\n"+toString()+"\"\"\");\n";
   }

//D1 Actions                                                                    // Actions on the stuck

  void clear() {stuckSize.zero();}                                              // Clear the stuck by making it appear empty

  void iClear()                                                                 // Clear the stuck by making it appear empty
   {L.P.new Instruction()
     {void action()
       {clear();
       }
     };
   }

  void push()                                                                   // Push a new key, data pair on the stack
   {if (stuckSize.value >= maxStuckSize)
     {L.stopProgram("Cannot push to a full stuck");
      return;
     }
    stuckKeys.write(stuckSize);
    stuckData.write(stuckSize);
    stuckSize.inc();
   }

  void iPush()                                                                  // Push a new key, data pair on the stack
   {L.P.new Instruction()
     {void action() {push();}
     };
   }

  void pop()                                                                    // Pop a key, data pair from the stack
   {if (!stuckSize.asBoolean())
     {L.stopProgram("Cannot pop an empty stuck");
      return;
     }
    stuckSize.dec();
    stuckKeys.read(stuckSize);
    stuckData.read(stuckSize);
   }

  void iPop()                                                                   // Pop a key, data pair from the stack
   {L.P.new Instruction()
     {void action()
       {pop();
       }
     };
   }

  void unshift()                                                                // Unshift a key, data pair into the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= maxStuckSize)
         {L.stopProgram("Cannot unshift into a full stuck");
          return;
         }

        for (int i = maxStuckSize; i > 1; --i)
         {stuckKeys.move(i-1, stuckKeys, i-2);
          stuckData.move(i-1, stuckData, i-2);
         }
        stuckKeys.move(0, stuckKeys);
        stuckData.move(0, stuckData);
        stuckSize.inc();
       }
     };
   }

  void shift()                                                                  // Shift a key, data pair from the stack after moving all the existing elements up one
   {L.P.new Instruction()
     {void action()
       {if (!stuckSize.asBoolean())
         {L.stopProgram("Cannot shift an empty stuck");
          return;
         }

        stuckKeys.read(0);
        stuckData.read(0);

        for (int i = 1; i < stuckSize.value; ++i)
         {stuckKeys.move(i-1, stuckKeys, i);
          stuckData.move(i-1, stuckData, i);
         }
        stuckSize.dec();
       }
     };
   }

  void firstElement()                                                           // Get the first key, data pair
   {if (!stuckSize.asBoolean())
     {L.stopProgram("Cannot get the first element because the stuck is empty");
      return;
     }

    stuckKeys.read(0);
    stuckData.read(0);
   }

  void iFirstElement()                                                          // Get the first key, data pair
   {L.P.new Instruction()
     {void action()
       {firstElement();
       }
     };
   }

  void lastElement()                                                            // Get the last key, data pair
   {if (!stuckSize.asBoolean())
     {L.stopProgram("Cannot get the last element because the stuck is empty");
      return;
     }

    stuckKeys.read(stuckSize.value-1);
    stuckData.read(stuckSize.value-1);
   }

  void iLastElement()                                                           // Get the last key, data pair
   {L.P.new Instruction()
     {void action()
       {lastElement();
       }
     };
   }

  void pastLastElement()                                                        // Get the key, data pair beyond the last valid element
   {if (stuckSize.value > maxStuckSize - 1)
     {L.stopProgram("Cannot get the element beyond the last element because the stuck is full");
      return;
     }

    stuckKeys.read(stuckSize.value);
    stuckData.read(stuckSize.value);
   }

  void iPastLastElement()                                                       // Get the key, data pair beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {pastLastElement();
       }
     };
   }

  void elementAt(Layout.Field index)                                           // Get the key, data pair at the specified index
   {if (index.value >= stuckSize.value)
     {L.stopProgram("Cannot get element beyond end of stuck");
      return;
     }

    stuckKeys.read(index.value);
    stuckData.read(index.value);
   }

  void iElementAt(Layout.Field Index)                                           // Get the key, data pair at the specified index
   {L.P.new Instruction()
     {void action()
       {elementAt(Index);
       }
     };
   }

  void setElementAt(Layout.Field index)                                         // Set the key, data pair at the specified index
   {if (index.value > stuckSize.value)
     {L.stopProgram("Cannot set element more than one step beyond current end of stuck");
      return;
     }
    if (index.value == stuckSize.value) stuckSize.inc();                        // Extending the stuck
    stuckKeys.write(index);
    stuckData.write(index);
   }

  void iSetElementAt(Layout.Field Index)                                        // Set the key, data pair at the specified index
   {L.P.new Instruction()
     {void action()
       {setElementAt(Index);
       }
     };
   }

  void setKeyAt(Layout.Field index)                                             // Set the key at the specified index
   {L.P.new Instruction()
     {void action()
       {if (index.value > stuckSize.value)
         {L.stopProgram("Cannot set key more than one step beyond current end of stuck");
          return;
         }
        if (index.value == stuckSize.value) stuckSize.inc();                    // Extending the stuck
        stuckKeys.write(index);
       }
     };
   }

  void setDataAt(Layout.Field index)                                            // Set the data element at the specified index
   {if (index.value > stuckSize.value)
     {L.stopProgram("Cannot set data more than one step beyond current end of stuck");
      return;
     }
    if (index.value == stuckSize.value) stuckSize.inc();                        // Extending the stuck
    stuckData.write(index);
   }

  void iSetDataAt(Layout.Field index)                                           // Set the data element at the specified index
   {L.P.new Instruction()
     {void action()
       {setDataAt(index);
       }
     };
   }

  void setFirstElement()                                                        // Set the first key, data pair
   {L.P.new Instruction()
     {void action()
       {if (!stuckSize.asBoolean())
         {L.stopProgram("Cannot set the first element because the stuck is empty");
          return;
         }
        stuckKeys.move(0, stuckKeys);
        stuckData.move(0, stuckData);
        if (stuckSize.value == 0) stuckSize.inc();
       }
     };
   }

  void setLastElement()                                                         // Set the last key, data pair
   {L.P.new Instruction()
     {void action()
       {if (!stuckSize.asBoolean())
         {L.stopProgram("Cannot set the last element because the stuck is empty");
          return;
         }
        stuckKeys.move(stuckSize.value-1, stuckKeys);
        stuckData.move(stuckSize.value-1, stuckData);
        if (stuckSize.value == 0) stuckSize.inc();
       }
     };
   }

  void setPastLastElement()                                                     // Set the key, data pair beyond the last valid element
   {if (stuckSize.value >= maxStuckSize)
     {L.stopProgram("Cannot set the element beyond the last element because the stuck is full");
      return;
     }

    stuckKeys.move(stuckSize.value, stuckKeys);
    stuckData.move(stuckSize.value, stuckData);
   }

  void iSetPastLastElement()                                                    // Set the key, data pair beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {setPastLastElement();
       }
     };
   }

  void setPastLastKey()                                                         // Set the key beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {if (stuckSize.value >= maxStuckSize)
         {L.stopProgram("Cannot set the key element beyond the last element because the stuck is full");
          return;
         }
        stuckKeys.move(stuckSize.value, stuckKeys);
       }
     };
   }

  void setPastLastData()                                                        // Set the data element beyond the last valid element
   {if (stuckSize.value >= maxStuckSize)
     {L.stopProgram("Cannot set the data element beyond the last element because the stuck is full");
      return;
     }
    stuckData.move(stuckSize.value, stuckData);
   }

  void iSetPastLastData()                                                        // Set the data element beyond the last valid element
   {L.P.new Instruction()
     {void action()
       {setPastLastData();
       }
     };
   }

  void insertElementAt(Layout.Field Index)                                      // Insert a key, data pair at the specified index moving the elements above this position up one place to make room
   {if (stuckSize.value >= maxStuckSize)
     {L.stopProgram("Cannot insert into a full stuck");
      return;
     }

    for (int i = maxStuckSize; i > Index.value+1; --i)
     {stuckKeys.move(i-1, stuckKeys, i-2);
      stuckData.move(i-1, stuckData, i-2);
     }

    stuckKeys.move(Index.value, stuckKeys);
    stuckData.move(Index.value, stuckData);
    stuckSize.inc();
   }

  void iInsertElementAt(Layout.Field Index)                                     // Insert a key, data pair at the specified index moving the elements above this position up one place to make room
   {L.P.new Instruction()
     {void action()
       {insertElementAt(Index);
       }
     };
   }

  void removeElementAt(Layout.Field Index)                                      // Get the value of the indexed key, data pair at the specified index moving the elements above down into this position
   {if (!stuckSize.asBoolean())
     {L.stopProgram("Cannot remove element from empty stuck");
      return;
     }
    if (Index.value >= stuckSize.value)
     {L.stopProgram("Cannot remove element beyond end of actual stuck");
      return;
     }

    stuckKeys.read(Index.value);
    stuckData.read(Index.value);

    for (int i = Index.value; i < maxStuckSize-1; ++i)
     {stuckKeys.move(i, stuckKeys, i+1);
      stuckData.move(i, stuckData, i+1);
     }
    stuckSize.dec();
   }

  void iRemoveElementAt(Layout.Field Index)                                     // Get the value of the indexed key, data pair at the specified index moving the elements above down into this position
   {L.P.new Instruction()
     {void action()
       {removeElementAt(Index);
       }
     };
   }

//D2 Search                                                                     // Search for a matching key in the stuck

  void search_eq(Layout.Field Found, Layout.Field Index)                        // Search for an equal key.
   {final int K = stuckKeys.value;                                              // Key being sought
    for (int i = 0; i < stuckSize.value; ++i)                                   // Check each key
     {stuckKeys.read(i);                                                        // Key being checked
      if (K == stuckKeys.value)                                                 // Search key versus current key
       {Found.value = 1; Index.value = i;
        stuckData.read(i);
        return;
       }
     }
    Found.write(0);
   }

  void iSearch_eq(Layout.Field Found, Layout.Field Index)                       // Search for an equal key.
   {L.P.new Instruction()
     {void action()
       {search_eq(Found, Index);
       }
     };
   }

  void search_le(Layout.Field Found, Layout.Field Index)                        // Search for the first key in the stuck less than or equal to the search key. The last key is not included in the search.  If a match is not found the last data element is returned itherwise the data element of the matching key
   {if (stuckSize.value >= maxStuckSize)
     {L.stopProgram("Cannot search a full stuck because the first element past the last element is needed as the return value if no key matches");
      return;
     }

    final int K = stuckKeys.value;                                              // Key being sought

    for (int i = 0; i < stuckSize.value; ++i)                                   // Check each key not including the last
     {stuckKeys.read(i);
      if (K <= stuckKeys.value)                                                 // Found a matching key
       {Found.value = 1; Index.value = i;
        stuckKeys.read(i);
        stuckData.read(i);
        return;
       }
     }
    Found.value = 0;
    stuckKeys.read(stuckSize.value);
    stuckData.read(stuckSize.value);
   }

  void iSearch_le(Layout.Field Found, Layout.Field Index)                       // Search for the first key in the stuck less than or equal to the search key. The last key is not included in the search.  If a match is not found the last data element is returned itherwise the data element of the matching key
   {L.P.new Instruction()
     {void action()
       {search_le(Found, Index);
       }
     };
   }

// Split                                                                        // Split a stuck in various ways

  void splitIntoTwo(Stuck Left, Stuck Right, int Copy)                          // Copy the first key, data pairs into the left stuck, the remainder into the right stuck.  The original source stuck is not modifiedr
   {if (Copy > stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Left.maxStuckSize  < Copy)
     {L.P.stopProgram("Left stuck too small");
      return;
     }
    if (Right.maxStuckSize < stuckSize.value - Copy)
     {L.P.stopProgram("Right stuck too small");
      return;
     }

    for (int i = 0; i < Copy; ++i)                                              // Copy to left
     {Left.stuckKeys.move(i, stuckKeys, i);
      Left.stuckData.move(i, stuckData, i);
     }
    Left.stuckSize.value = Copy;                                                // New size of left

    for (int i = 0; i < stuckSize.value - Copy; ++i)                            // Copy to right
     {Right.stuckKeys.move(i, stuckKeys, Copy + i);
      Right.stuckData.move(i, stuckData, Copy + i);
     }
    Right.stuckSize.value = stuckSize.value - Copy;                             // New size of right
   }

  void iSplitIntoTwo(Stuck Left, Stuck Right, int Copy)                         // Copy the first key, data pairs into the left stuck, the remainder into the right stuck.  The original source stuck is not modifiedr
   {L.P.new Instruction()
     {void action()
       {splitIntoTwo(Left, Right, Copy);
       }
     };
   }

  void splitIntoThree(Stuck Left, Stuck Right, int Copy)                        // Copy the specified number of key, data pairs into the left stuck, skip one pair, then copy the specified number onto into the right stuck
   {if (Copy >= stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Left.maxStuckSize  <  Copy)
     {L.P.stopProgram("Left stuck too small");
      return;
     }
    if (Right.maxStuckSize <  Copy)
     {L.P.stopProgram("Right stuck too small");
      return;
     }

    for (int i = 0; i < Copy; ++i)                                              // Copy to left
     {Left.stuckKeys.move(i, stuckKeys, i);
      Left.stuckData.move(i, stuckData, i);
     }
    Left.stuckSize.value = Copy;                                                // New size of left
    Left.stuckData.move(Copy, stuckData, Copy);

    for (int i = 0; i < Copy; ++i)                                              // Copy to right
     {Right.stuckKeys.move(i, stuckKeys, Copy + i + 1);
      Right.stuckData.move(i, stuckData, Copy + i + 1);
     }
    Right.stuckSize.value = Copy;                                               // New size of right
    Right.stuckData.move(Copy, stuckData, 2*Copy+1);
   }

  void iSplitIntoThree(Stuck Left, Stuck Right, int Copy)                       // Copy the specified number of key, data pairs into the left stuck, skip one pair, then copy the specified number onto into the right stuck
   {L.P.new Instruction()
     {void action()
       {splitIntoThree(Left, Right, Copy);
       }
     };
   }

  void splitLow(Stuck Left, int Copy)                                           // Copy the specified number of key, data pairs into the left stuck then move the remainder down
   {if (Copy >= stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Left.maxStuckSize  <  Copy)
     {L.P.stopProgram("Left stuck too small");
      return;
     }

    for (int i = 0; i < Copy; ++i)                                              // Copy to left
     {Left.stuckKeys.move(i, stuckKeys, i);
      Left.stuckData.move(i, stuckData, i);
     }
    Left.stuckSize.value = Copy;                                                // New size of left

    for (int i = 0; i < Copy; ++i)                                              // Move down right
     {stuckKeys.move(i, stuckKeys, Copy + i);
      stuckData.move(i, stuckData, Copy + i);
     }
    stuckSize.value = Copy;                                                     // New size of right
   }

  void iSplitLow(Stuck Left, int Copy)                                          // Copy the specified number of key, data pairs into the left stuck then move the remainder down
   {L.P.new Instruction()
     {void action()
       {splitLow(Left, Copy);
       }
     };
   }

  void splitLowButOne(Stuck Left, int Copy, Layout.Field One)                   // Copy the specified number of key, data pairs into the left stuck then place the key in "one", key then move the remainder down
   {if (Copy >= stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Left.maxStuckSize  <  Copy)
     {L.P.stopProgram("Left stuck too small");
      return;
     }

    for (int i = 0; i < Copy; ++i)                                              // Copy to left
     {Left.stuckKeys.move(i, stuckKeys, i);
      Left.stuckData.move(i, stuckData, i);
     }
    Left.stuckData.move(Copy, stuckData, Copy);
    Left.stuckSize.value = Copy;                                                // New size of left

    stuckKeys.read(Copy); One.move(stuckKeys);                                  // Central key

    for (int i = 0; i < Copy; ++i)                                              // Move down right
     {stuckKeys.move(i, stuckKeys, Copy + i+1);
      stuckData.move(i, stuckData, Copy + i+1);
     }
    stuckData.move(Copy, stuckData, 2*Copy+1);
    stuckSize.value = Copy;                                                     // New size of right
   }

  void iSplitLowButOne(Stuck Left, int Copy, Layout.Field One)                  // Copy the specified number of key, data pairs into the left stuck then place the key in "one", key then move the remainder down
   {L.P.new Instruction()
     {void action()
       {splitLowButOne(Left, Copy, One);                                         // Copy the specified number of key, data pairs into the left stuck then place the key in "one", key then move the remainder down
       }
     };
   }

  void splitHigh(Stuck Right, int Copy)                                         // Leave the specified number of key, data pairs in the left stuck, then copy the specified number of following key, data pairs onto into the right stuck
   {if (Copy >= stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Right.maxStuckSize <  Copy)
     {L.P.stopProgram("Right stuck too small");
      return;
     }

    stuckSize.value = Copy;                                                     // New size of left

    for (int i = 0; i < Copy; ++i)                                              // Copy to right
     {Right.stuckKeys.move(i, stuckKeys, Copy + i);
      Right.stuckData.move(i, stuckData, Copy + i);
     }
    Right.stuckSize.value = Copy;                                               // New size of right
   }

  void iSplitHigh(Stuck Right, int Copy)                                        // Leave the specified number of key, data pairs in the left stuck, then copy the specified number of following key, data pairs onto into the right stuck
   {L.P.new Instruction()
     {void action()
       {splitHigh(Right, Copy);
       }
     };
   }

  void splitHighButOne(Stuck Right, int Copy, Layout.Field One)                 // Leave the specified number of key, data pairs in the left stuck, move the central keey to "one", then copy the specified number of following key, data pairs onto into the right stuck
   {if (Copy >= stuckSize.value)
     {L.P.stopProgram("Cannot copy beyond end of stuck");
      return;
     }
    if (Right.maxStuckSize < Copy)
     {L.P.stopProgram("Right stuck too small");
      return;
     }

    stuckSize.value = Copy;                                                     // New size of left
    stuckKeys.read(Copy); One.move(stuckKeys);                                  // Central key

    for (int i = 0; i < Copy; ++i)                                              // Copy to right
     {Right.stuckKeys.move(i, stuckKeys, Copy + i + 1);
      Right.stuckData.move(i, stuckData, Copy + i + 1);
     }
    Right.stuckData.move(Copy, stuckData, 2*Copy+1);
    Right.stuckSize.value = Copy;                                               // New size of right
   }

  void iSplitHighButOne(Stuck Right, int Copy, Layout.Field One)                // Leave the specified number of key, data pairs in the left stuck, move the central keey to "one", then copy the specified number of following key, data pairs onto into the right stuck
   {L.P.new Instruction()
     {void action()
       {splitHighButOne(Right, Copy, One);                                      // Leave the specified number of key, data pairs in the left stuck, move the central keey to "one", then copy the specified number of following key, data pairs onto into the right stuck
       }
     };
   }

//D1 Merge                                                                      // Merge stucks in various weays

  void merge(Stuck source, Layout.Field success)                                // Concatenate the indicated stuck on to the end of the current one
   {final int sourceSize = source.stuckSize.value;
    final int targetSize =        stuckSize.value;
    if (sourceSize + targetSize > maxStuckSize)
     {success.value = 0;
      return;
     }
    for (int i = 0; i < sourceSize; ++i)                                        // Concatenate each key, data pair
     {stuckKeys.move(targetSize+i, source.stuckKeys, i);
      stuckData.move(targetSize+i, source.stuckData, i);
     }
    stuckSize.value += sourceSize;                                              // New size of target
    success.value = 1;
   }

  void iMerge(Stuck source, Layout.Field success)                               // Concatenate the indicated stuck on to the end of the current one
   {L.P.new Instruction()
     {void action()
       {merge(source, success);
       }
     };
   }

  void merge(Stuck Left, Stuck Right, Layout.Field success)                     // Replace the current stuck with the concatenation of the left and right stucks
   {final int leftSize  = Left .stuckSize.value;
    final int rightSize = Right.stuckSize.value;
    if (leftSize + rightSize > maxStuckSize)
     {success.value = 0;
      return;
     }
    for (int i = 0; i < leftSize; ++i)                                          // Copy in left
     {stuckKeys.move(i, Left.stuckKeys, i);
      stuckData.move(i, Left.stuckData, i);
     }
    for (int i = 0; i < rightSize; ++i)                                         // Copy in right
     {stuckKeys.move(leftSize+i, Right.stuckKeys, i);
      stuckData.move(leftSize+i, Right.stuckData, i);
     }
    stuckSize.value = leftSize + rightSize;                                     // New size of target
    success.value = 1;
   }

  void iMerge(Stuck Left, Stuck Right, Layout.Field Success)                    // Replace the current stuck with the concatenation of the left and right stucks
   {L.P.new Instruction()
     {void action()
       {merge(Left, Right, Success);
       }
     };
   }

  void mergeButOne(Layout.Field Key, Stuck source, Layout.Field success)        // Concatenate the indicated stuck on to the end of the current one with the key inserted over the past last data element separating the two
   {final int sourceSize = source.stuckSize.value;
    final int targetSize =        stuckSize.value;
    if (sourceSize + targetSize + 1 >= maxStuckSize)                            // Check size
     {success.value = 0;
      return;
     }
    stuckKeys.move(targetSize, Key);                                            // Add key over past last data element
    for (int i = 0; i < sourceSize; ++i)                                        // Concatenate each key, data pair from source
     {stuckKeys.move(targetSize+i+1, source.stuckKeys, i);
      stuckData.move(targetSize+i+1, source.stuckData, i);
     }
    stuckData.move(targetSize+sourceSize+1, source.stuckData, sourceSize);      // Past last data element from source
    stuckSize.value += sourceSize + 1;                                          // New size of target
    success.value = 1;
   }

  void iMergeButOne(Layout.Field Key, Stuck Source, Layout.Field Success)       // Concatenate the indicated stuck on to the end of the current one with the key inserted over the past last data element separating the two
   {L.P.new Instruction()
     {void action()
       {mergeButOne(Key, Source, Success);                                      // Concatenate the indicated stuck on to the end of the current one with the key inserted over the past last data element separating the two
       }
     };
   }

  void mergeButOne                                                              // Concatenate the left and right stucks separated by the key over th past last data element of the left stuck into the target
   (Stuck Left, Layout.Field Key, Stuck Right, Layout.Field success)
   {final int leftSize  = Left .stuckSize.value;
    final int rightSize = Right.stuckSize.value;
    if (leftSize + rightSize + 1 >= maxStuckSize)                               // Check size
     {success.value = 0;
      return;
     }
    for (int i = 0; i < leftSize; ++i)                                          // Concatenate each key, data pair from source
     {stuckKeys.move(i, Left.stuckKeys, i);
      stuckData.move(i, Left.stuckData, i);
     }
    stuckKeys.move(leftSize, Key);                                              // Place key over past last data element from left
    stuckData.move(leftSize, Left.stuckData, leftSize);

    for (int i = 0; i < rightSize; ++i)                                         // Concatenate each key, data pair from right
     {stuckKeys.move(leftSize+i+1, Right.stuckKeys, i);
      stuckData.move(leftSize+i+1, Right.stuckData, i);
     }
    stuckData.move(leftSize+rightSize+1, Right.stuckData, rightSize);           // Past last data element from source

    stuckSize.value = leftSize + rightSize + 1;                                 // New size of target
    success.one();
   }

  void iMergeButOne                                                             // Concatenate the left and right stucks separated by the key over th past last data element of the left stuck into the target
   (Stuck Left, Layout.Field Key, Stuck Right, Layout.Field Success)
   {L.P.new Instruction()
     {void action()
       {mergeButOne(Left, Key, Right, Success);                                 // Concatenate the left and right stucks separated by the key over th past last data element of the left stuck into the target
       }
     };
   }

//D1 Tests                                                                      // Tests

  static void test_parse()                                                      // Parse the stuck
   {final Stuck a = new Stuck(4, 4, 4);

    //stop(a.L);
    ok(a.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           0  var        3
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         0  var        4   Stuck                        4
  3       2    stuckData         0  var        4   Stuck                        4
""");
   }

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

    s.clearProgram(); k.iWrite(1); d.iWrite(2); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(2); d.iWrite(4); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(3); d.iWrite(6); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(4); d.iWrite(8); s.iPush(); s.runProgram();

    ok(s, """
stuckSize: value=4
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=8, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    k.iWrite(5); d.iWrite(10); s.iPush();
    s.runProgram();
    ok(s.L.P.rc, "Cannot push to a full stuck");

    s.clearProgram(); k.iWrite(0); d.iWrite(0); s.runProgram();                 // Clean up key and data value

    return s;
   }

  protected static void test_clear()
   {final Stuck s = test_push();

    s.iClear();
    s.runProgram();
    ok(s, """
stuckSize: value=0
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");
   }

  protected static void test_copy()
   {final Stuck s = test_push();
    final Stuck t = s.duplicate();

    ok(t, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");
   }

  protected static Stuck test_pop()
   {final Stuck  s = test_push();

    s.clearProgram();
    s.iPop();
    s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=8, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram(); s.iPop(); s.runProgram();
    ok(s, """
stuckSize: value=2
stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4
stuckData: value=6, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram(); s.iPop(); s.runProgram();
    ok(s, """
stuckSize: value=1
stuckKeys: value=2, 0=1, 1=2, 2=3, 3=4
stuckData: value=4, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram(); s.iPop(); s.runProgram();
    ok(s, """
stuckSize: value=0
stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4
stuckData: value=2, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iPop();
    s.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot pop an empty stuck");

    return s;
   }

  protected static Stuck test_unshift()
   {final Stuck  s = test_push();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.clearProgram(); s.iPop(); s.runProgram();
    s.clearProgram(); k.iWrite(9); d.iWrite(11); s.unshift(); s.runProgram();

    ok(s, """
stuckSize: value=4
stuckKeys: value=9, 0=9, 1=1, 2=2, 3=3
stuckData: value=11, 0=11, 1=2, 2=4, 3=6
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    k.iWrite(9); d.iWrite(11); s.unshift();
    s.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot unshift into a full stuck");

    return s;
   }

  protected static Stuck test_shift()
   {final Stuck s = test_push();

    s.clearProgram(); s.shift(); s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=1, 0=2, 1=3, 2=4, 3=4
stuckData: value=2, 0=4, 1=6, 2=8, 3=8
""");

    s.clearProgram(); s.shift(); s.runProgram();
    ok(s, """
stuckSize: value=2
stuckKeys: value=2, 0=3, 1=4, 2=4, 3=4
stuckData: value=4, 0=6, 1=8, 2=8, 3=8
""");

    s.clearProgram(); s.shift(); s.runProgram();
    ok(s, """
stuckSize: value=1
stuckKeys: value=3, 0=4, 1=4, 2=4, 3=4
stuckData: value=6, 0=8, 1=8, 2=8, 3=8
""");

    s.clearProgram(); s.shift(); s.runProgram();
    ok(s, """
stuckSize: value=0
stuckKeys: value=4, 0=4, 1=4, 2=4, 3=4
stuckData: value=8, 0=8, 1=8, 2=8, 3=8
""");

    s.L.P.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.shift();
    s.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot shift an empty stuck");

    return s;
   }

  protected static void test_elementAt()
   {final Stuck s = test_push();
    Layout.Field index = s.index();

    s.clearProgram(); index.iWrite(2); s.iElementAt(index); s.runProgram();
    //stop(s.L);
    ok(s.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           4  var        3
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         3  var        4   Stuck                        4
  3       2    stuckData         6  var        4   Stuck                        4
  0       0  stuckIndex          2  var        3
""");

    s.clearProgram(); index.iWrite(1); s.iElementAt(index); s.runProgram();
    //stop(s.L);
    ok(s.L, """
  #  Indent  Name         Value___  Command  Rep  Parent  Children              Dimension
  0       0  stuckSize           4  var        3
  1       0  Stuck               0  array      4          stuckKeys, stuckData
  2       2    stuckKeys         2  var        4   Stuck                        4
  3       2    stuckData         4  var        4   Stuck                        4
  0       0  stuckIndex          1  var        3
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(5); s.iElementAt(index);
    s.runProgram();
    //stop(s.L.P.rc);
    ok(s.L.P.rc, "Cannot get element beyond end of stuck");
   }

  protected static void test_setElementAt()
   {final Stuck s = test_push();
    Layout.Field index = s.index();

    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");
    s.clearProgram();
    index.iWrite(1);
    s.stuckKeys.iWrite(9);
    s.stuckData.iWrite(11);
    s.iSetElementAt(index);
    s.runProgram();

    ok(s, """
stuckSize: value=4
stuckKeys: value=9, 0=1, 1=9, 2=3, 3=4
stuckData: value=11, 0=2, 1=11, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iPop();
    s.iPop();
    index.iWrite(3);
    s.iSetElementAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot set element more than one step beyond current end of stuck");

    s.clearProgram();
    index.iWrite(2);
    s.stuckKeys.iWrite(8);
    s.stuckData.iWrite(12);
    s.iSetElementAt(index);
    s.runProgram();

    ok(s, """
stuckSize: value=3
stuckKeys: value=8, 0=1, 1=9, 2=8, 3=4
stuckData: value=12, 0=2, 1=11, 2=12, 3=8
""");
   }

  protected static void test_setKeyAt()
   {final Stuck s = test_push();
    Layout.Field index = s.index();
    s.clearProgram();
    index.iWrite(1);
    s.stuckKeys.iWrite(9);
    s.stuckData.iWrite(11);
    s.setKeyAt(index);
    s.runProgram();

    ok(s, """
stuckSize: value=4
stuckKeys: value=9, 0=1, 1=9, 2=3, 3=4
stuckData: value=11, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iPop();
    s.iPop();
    index.iWrite(3);
    s.setKeyAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot set key more than one step beyond current end of stuck");
   }

  protected static void test_setData()
   {final Stuck s = test_push();
    Layout.Field index = s.index();
    s.clearProgram();
    index.iWrite(1);
    s.stuckKeys.iWrite(9);
    s.stuckData.iWrite(11);
    s.iSetDataAt(index);
    s.runProgram();

    ok(s, """
stuckSize: value=4
stuckKeys: value=9, 0=1, 1=2, 2=3, 3=4
stuckData: value=11, 0=2, 1=11, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iPop();
    s.iPop();
    index.iWrite(3);
    s.iSetDataAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot set data more than one step beyond current end of stuck");
   }

  protected static void test_insertElementAt()
   {final Stuck s = test_push();
    Layout.Field index = s.index();
    s.clearProgram(); s.iPop(); s.runProgram();

    ok(s, """
stuckSize: value=3
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=8, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram(); index.iWrite(1); s.stuckKeys.iWrite(9);  s.stuckData.iWrite(9); s.iInsertElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=4
stuckKeys: value=9, 0=1, 1=9, 2=2, 3=3
stuckData: value=9, 0=2, 1=9, 2=4, 3=6
""");

    s.clearProgram(); s.iPop(); s.runProgram();
    s.clearProgram(); index.iWrite(1); s.stuckKeys.iWrite(10);  s.stuckData.iWrite(12); s.iInsertElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=4
stuckKeys: value=10, 0=1, 1=10, 2=9, 3=2
stuckData: value=12, 0=2, 1=12, 2=9, 3=4
""");

    s.clearProgram(); s.iPop(); s.runProgram();
    s.clearProgram(); index.iWrite(0); s.stuckKeys.iWrite(11);  s.stuckData.iWrite(13); s.iInsertElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=4
stuckKeys: value=11, 0=11, 1=1, 2=10, 3=9
stuckData: value=13, 0=13, 1=2, 2=12, 3=9
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(0); s.stuckKeys.iWrite(12);  s.stuckData.iWrite(14); s.iInsertElementAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot insert into a full stuck");
   }

  protected static void test_removeElementAt()
   {final Stuck s = test_push();
    Layout.Field index = s.index();

    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram(); index.iWrite(1); s.iRemoveElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=2, 0=1, 1=3, 2=4, 3=4
stuckData: value=4, 0=2, 1=6, 2=8, 3=8
""");

    s.clearProgram(); index.iWrite(1); s.iRemoveElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=2
stuckKeys: value=3, 0=1, 1=4, 2=4, 3=4
stuckData: value=6, 0=2, 1=8, 2=8, 3=8
""");

    s.clearProgram(); index.iWrite(1); s.iRemoveElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=1
stuckKeys: value=4, 0=1, 1=4, 2=4, 3=4
stuckData: value=8, 0=2, 1=8, 2=8, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(1); s.iRemoveElementAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot remove element beyond end of actual stuck");

    s.clearProgram(); index.iWrite(0); s.iRemoveElementAt(index); s.runProgram();
    ok(s, """
stuckSize: value=0
stuckKeys: value=1, 0=4, 1=4, 2=4, 3=4
stuckData: value=2, 0=8, 1=8, 2=8, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    index.iWrite(0); s.iRemoveElementAt(index);
    s.runProgram();
    ok(s.L.P.rc, "Cannot remove element from empty stuck");
   }

  protected static void test_search_eq()
   {final Stuck s = test_push();
    Layout.Field found = s.found();
    Layout.Field index = s.index();

    s.clearProgram(); s.stuckKeys.iWrite(2); s.iSearch_eq(found, index); s.runProgram();
    ok(found.asBoolean(), true);
    ok(index.value, 1);
    ok(s.stuckKeys.value, 2);
    ok(s.stuckData.value, 4);

    s.clearProgram(); s.stuckKeys.iWrite(5); s.iSearch_eq(found, index); s.runProgram();
    ok(found.value, 0);
   }

  protected static void test_search_le()
   {final Stuck  s = testStuck();

    Layout.Field k = s.stuckKeys;
    Layout.Field d = s.stuckData;

    s.clearProgram(); k.iWrite(2); d.iWrite(3); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(4); d.iWrite(5); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(6); d.iWrite(7); s.iPush(); s.runProgram();
    s.clearProgram(); k.iWrite(8); d.iWrite(9); s.iSetPastLastElement(); s.runProgram();

    Layout.Field found = s.found();
    Layout.Field index = s.index();

    s.clearProgram(); s.stuckKeys.iWrite(2); s.iSearch_le(found, index); s.runProgram();
    ok(found.asBoolean(), true);
    ok(index.value, 0);
    ok(s.stuckKeys.value, 2);
    ok(s.stuckData.value, 3);

    s.clearProgram(); s.stuckKeys.iWrite(3); s.iSearch_le(found, index); s.runProgram();
    ok(found.asBoolean(), true);
    ok(index.value, 1);
    ok(s.stuckKeys.value, 4);
    ok(s.stuckData.value, 5);

    s.clearProgram(); s.stuckKeys.iWrite(10); s.iSearch_le(found, index); s.runProgram();
    ok(found.value, 0);
   }

  protected static void test_merge()
   {final Stuck s = test_push();
    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck t = test_push(); t.L.P = s.L.P;
    final Stuck m = test_push(); m.L.P = s.L.P;
    final Layout.Field success = s.success();

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iMerge(t, success);
    s.runProgram();
    ok(success, "success: value=0");

    s.clearProgram();
    s.iPop();
    s.iPop();
    t.iPop();
    t.iPop();
    s.iMerge(t, success);
    s.runProgram();
    ok(success, "success: value=1");
    ok(s, """
stuckSize: value=4
stuckKeys: value=3, 0=1, 1=2, 2=1, 3=2
stuckData: value=6, 0=2, 1=4, 2=2, 3=4
""");

    m.clearProgram();
    m.L.P.supressErrorMessagePrint = true;
    m.iMerge(s, t, success);
    m.runProgram();
    ok(success, "success: value=0");

    m.clearProgram();
    s.iPop();
    s.iPop();
    m.iMerge(s, t, success);
    m.runProgram();
    ok(success, "success: value=1");
    ok(m, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=1, 3=2
stuckData: value=0, 0=2, 1=4, 2=2, 3=4
""");
   }

  protected static void test_mergeButOne()
   {final Stuck s = test_push();
    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck t = test_push(); t.L.P = s.L.P;
    final Stuck m = test_push(); m.L.P = s.L.P;
    final Layout.Field success = s.success();
    final Layout.Field k = t.key(11);

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iMergeButOne(k, t, success);
    s.runProgram();
    ok(success, "success: value=0");

    s.clearProgram();
    s.iPop(); s.iPop(); s.iPop();
    t.iPop(); t.iPop(); t.iPop();
    s.iMergeButOne(k, t, success);
    s.runProgram();
    ok(success, "success: value=1");
    ok(s, """
stuckSize: value=3
stuckKeys: value=2, 0=1, 1=11, 2=1, 3=4
stuckData: value=4, 0=2, 1=4, 2=2, 3=4
""");

    m.clearProgram();
    m.L.P.supressErrorMessagePrint = true;
    m.iMergeButOne(s, k, t, success);
    m.runProgram();
    ok(success, "success: value=0");

    m.clearProgram();
    s.iPop(); s.iPop();
    m.iMergeButOne(s, k, t, success);
    m.runProgram();
    ok(success, "success: value=1");
    ok(m, """
stuckSize: value=3
stuckKeys: value=0, 0=1, 1=11, 2=1, 3=4
stuckData: value=0, 0=2, 1=4, 2=2, 3=4
""");
   }

  protected static void test_splitIntoTwo()
   {final Stuck s = test_push();
    final Stuck S = testSmallStuck(); S.L.P = s.L.P;
    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck L = test_push(); L.L.P = s.L.P;
    final Stuck R = test_push(); R.L.P = s.L.P;

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iSplitIntoTwo(L, R, 6);
    s.runProgram();
    ok(s.L.P.rc, "Cannot copy beyond end of stuck");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iSplitIntoTwo(S, R, 3);
    s.runProgram();
    ok(s.L.P.rc, "Left stuck too small");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iSplitIntoTwo(L, S, 1);
    s.runProgram();
    ok(s.L.P.rc, "Right stuck too small");

    s.clearProgram();
    s.iSplitIntoTwo(L, R, 2);
    s.runProgram();

    ok(L, """
stuckSize: value=2
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(R, """
stuckSize: value=2
stuckKeys: value=0, 0=3, 1=4, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_splitIntoThree()
   {final Stuck s = test_push();

    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck L = test_push(); L.L.P = s.L.P;
    final Stuck R = test_push(); R.L.P = s.L.P;

    s.clearProgram();
    s.iPop();
    s.iSplitIntoThree(L, R, 1);
    s.runProgram();

    ok(L, """
stuckSize: value=1
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(R, """
stuckSize: value=1
stuckKeys: value=0, 0=3, 1=2, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_splitLow()
   {final Stuck R = test_push();

    ok(R, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck L = test_push(); L.L.P = R.L.P;

    R.clearProgram();
    R.iSplitLow(L, 2);
    R.runProgram();

    ok(L, """
stuckSize: value=2
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(R, """
stuckSize: value=2
stuckKeys: value=0, 0=3, 1=4, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_splitHigh()
   {final Stuck L = test_push();

    ok(L, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck R = test_push(); R.L.P = L.L.P;

    L.clearProgram();
    L.iSplitHigh(R, 2);
    L.runProgram();

    ok(L, """
stuckSize: value=2
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(R, """
stuckSize: value=2
stuckKeys: value=0, 0=3, 1=4, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_splitLowButOne()
   {final Stuck R = test_push();
    final Layout.Field key = R.key();

    ok(R, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck L = test_push(); L.L.P = R.L.P;

    R.clearProgram();
    R.iSplitLowButOne(L, 1, key);
    R.runProgram();

    ok(L, """
stuckSize: value=1
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(key, "key: value=2");

    ok(R, """
stuckSize: value=1
stuckKeys: value=2, 0=3, 1=2, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_splitHighButOne()
   {final Stuck L = test_push();
    final Layout.Field key = L.key();

    ok(L, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    final Stuck R = test_push(); R.L.P = L.L.P;

    L.clearProgram();
    L.iSplitHighButOne(R, 1, key);
    L.runProgram();

    ok(L, """
stuckSize: value=1
stuckKeys: value=2, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    ok(key, "key: value=2");

    ok(R, """
stuckSize: value=1
stuckKeys: value=0, 0=3, 1=2, 2=3, 3=4
stuckData: value=0, 0=6, 1=8, 2=6, 3=8
""");
   }

  protected static void test_firstLastPast()
   {final Stuck s = test_push();

    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.iFirstElement();
    s.runProgram();
    ok(s, """
stuckSize: value=4
stuckKeys: value=1, 0=1, 1=2, 2=3, 3=4
stuckData: value=2, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.iPop();
    s.iLastElement();
    s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=3, 0=1, 1=2, 2=3, 3=4
stuckData: value=6, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.iPastLastElement();
    s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=4, 0=1, 1=2, 2=3, 3=4
stuckData: value=8, 0=2, 1=4, 2=6, 3=8
""");
   }

  protected static void test_setFirstLastPast()
   {final Stuck s = test_push();

    ok(s, """
stuckSize: value=4
stuckKeys: value=0, 0=1, 1=2, 2=3, 3=4
stuckData: value=0, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iPastLastElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot get the element beyond the last element because the stuck is full");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iSetPastLastElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot set the element beyond the last element because the stuck is full");

    s.clearProgram();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setFirstElement();
    s.runProgram();
    ok(s, """
stuckSize: value=4
stuckKeys: value=2, 0=2, 1=2, 2=3, 3=4
stuckData: value=2, 0=2, 1=4, 2=6, 3=8
""");

    s.clearProgram();
    s.iPop();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.setLastElement();
    s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=2, 0=2, 1=2, 2=2, 3=4
stuckData: value=2, 0=2, 1=4, 2=2, 3=8
""");

    s.clearProgram();
    s.stuckKeys.iWrite(2);
    s.stuckData.iWrite(2);
    s.iSetPastLastElement();
    s.runProgram();
    ok(s, """
stuckSize: value=3
stuckKeys: value=2, 0=2, 1=2, 2=2, 3=2
stuckData: value=2, 0=2, 1=4, 2=2, 3=2
""");

    s.clearProgram();
    s.iClear();
    s.L.P.supressErrorMessagePrint = true;
    s.iFirstElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot get the first element because the stuck is empty");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.iLastElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot get the last element because the stuck is empty");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.setFirstElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot set the first element because the stuck is empty");

    s.clearProgram();
    s.L.P.supressErrorMessagePrint = true;
    s.setLastElement();
    s.runProgram();
    ok(s.L.P.rc, "Cannot set the last element because the stuck is empty");

    s.clearProgram();
    s.stuckKeys.iWrite(11);
    s.stuckData.iWrite(11);
    s.iSetPastLastElement();
    s.runProgram();
    //stop(s);
    ok(s, """
stuckSize: value=0
stuckKeys: value=11, 0=11, 1=2, 2=2, 3=2
stuckData: value=11, 0=11, 1=4, 2=2, 3=2
""");

    s.clearProgram();
    s.stuckSize.iWrite(1);
    s.stuckKeys.iWrite(12);
    s.stuckData.iWrite(12);
    s.setPastLastKey();
    s.runProgram();
    //stop(s);
    ok(s, """
stuckSize: value=1
stuckKeys: value=12, 0=11, 1=12, 2=2, 3=2
stuckData: value=12, 0=11, 1=4, 2=2, 3=2
""");

    s.clearProgram();
    s.iSetPastLastData();
    s.runProgram();
    //stop(s);
    ok(s, """
stuckSize: value=1
stuckKeys: value=12, 0=11, 1=12, 2=2, 3=2
stuckData: value=12, 0=11, 1=12, 2=2, 3=2
""");
   }

  protected static void test_emptyFull()
   {final Stuck s = test_push();
    final Layout.Field e = s.empty();
    final Layout.Field f = s.full();
    final Layout.Field F = s.fullButOne();

    s.clearProgram();
    s.iIsEmpty(e);
    s.iIsFull (f);
    s.runProgram();
    ok(e, "empty: value=0");
    ok(f, "full: value=1");
    ok(F, "fullButOne: value=0");

    s.clearProgram();
    s.iPop();
    s.iIsEmpty     (e);
    s.iIsFull      (f);
    s.iIsFullButOne(F);
    s.runProgram();
    ok(e, "empty: value=0");
    ok(f, "full: value=0");
    ok(F, "fullButOne: value=1");

    s.clearProgram();
    s.iPop(); s.iPop(); s.iPop();
    s.iIsEmpty     (e);
    s.iIsFull      (f);
    s.iIsFullButOne(F);
    s.runProgram();
    ok(e, "empty: value=1");
    ok(f, "full: value=0");
    ok(F, "fullButOne: value=0");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_parse();
    test_push();
    test_clear();
    test_copy();
    test_pop();
    test_unshift();
    test_shift();
    test_elementAt();
    test_setElementAt();
    test_setKeyAt();
    test_setData();
    test_insertElementAt();
    test_removeElementAt();
    test_search_eq();
    test_search_le();
    test_merge();
    test_mergeButOne();
    test_splitIntoTwo();
    test_splitIntoThree();
    test_splitLow();
    test_splitHigh();
    test_splitLowButOne();
    test_splitHighButOne();
    test_firstLastPast();
    test_setFirstLastPast();
    test_emptyFull();
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
