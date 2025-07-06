//------------------------------------------------------------------------------
// Btree using block memory
// Philip R Brenan at appaapps dot com, Appa Apps Ltd Inc., 2025
//------------------------------------------------------------------------------
package com.AppaApps.Silicon;                                                   // Btree in a block on the surface of a silicon chip.

import java.util.*;

class Btree extends Test                                                        // Manipulate a btree in a block of memory
 {final int size;                                                               // The maximum number of nodes == branches or leaves in the btree
  final int stuckSize;                                                          // The maximum number of entries in the stuck.
  final int bitsPerKey;                                                         // The number of bits needed to define a key
  final int bitsPerData;                                                        // The number of bits needed to define a data field
  final Layout L;                                                               // Layout of the stuck
  final Layout.Field freeStartField;                                            // Start of free chain. Initially all stucks are on the free chain except the root stuck
  final Layout.Field stuckIsLeafField;                                          // Whether the current stuck is acting as a leaf or a branch in the btree.
  final Layout.Field stuckIsFreeField;                                          // Whether the stuck is on the free chain
  final Layout.Field freeNextField;                                             // Next stuck on the free chain. If this stuck is not on the free chain then this field is zero to show that this stuck in use
  final Layout.Field stuckSizeField;                                            // Current size of stuck up to the maximum size
  final Layout.Field stuckKeysField;                                            // Keys field
  final Layout.Field stuckDataField;                                            // Data field

//D1 Construction                                                               // Construct and layout a btree

  Btree(int Size, int StuckSize, int BitsPerKey, int BitsPerData)               // Create the Btree
   {size             = Size;                                                    // The maximum number of entries in the btree.
    stuckSize        = StuckSize;                                               // The maximum number of entries in the stuck.
    bitsPerKey       = BitsPerKey;                                              // The number of bits needed to define a key
    bitsPerData      = BitsPerData;                                             // The number of bits needed to define a data field
    L                = layout();                                                // Layout of the btree
    freeStartField   = L.locateFieldByName("freeStart");                        // Start of free chain. Initially all sticks are on the free chain except the root stuck
    stuckIsLeafField = L.locateFieldByName("stuckIsLeaf");                      // Whether the stuck is a leaf
    stuckIsFreeField = L.locateFieldByName("stuckIsFree");                      // Whether the stuck is on the free chain
    freeNextField    = L.locateFieldByName("freeNext");                         // Next element refernce on free chain
    stuckSizeField   = L.locateFieldByName("stuckSize");                        // Current size of stuck up to the maximum size
    stuckKeysField   = L.locateFieldByName("stuckKeys");                        // Keys field
    stuckDataField   = L.locateFieldByName("stuckData");                        // Data field

    createFreeChain();                                                          // Create the free chain
   }

  Layout layout()                                                               // Layout describing Btree.
   {if (logTwo(size) >= bitsPerData)                                            // The data field must be big enought to act as a node pointer in branches and a data pointer in leaves to locate the actual dfata held else where
     {stop("Bits per data too small for tree of this size");
     }
    return new Layout(String.format("""
freeStart      var    %d
stucks         array  %d
  stuckIsLeaf  bit
  stuckIsFree  bit
  freeNext     var    %d
  stuckSize    var    %d
  stuck        array  %d
    stuckKeys  var    %d
    stuckData  var    %d
""", logTwo(size), size, logTwo(size), logTwo(stuckSize), stuckSize, bitsPerKey, bitsPerData));
   }

  Layout.Field variable(String name, int size)                                  // Create a variable
   {final Layout.Field v = L.variable(name, size);
    v.layout.P = L.P;
    return v;
   }

  Layout.Field bit(String name) {return variable(name, 1);}                     // Create a bit
  Layout.Field btreeIndex()     {return variable("btreeIndex", logTwo(size)+1);}// Create an index for a stuck in a btree

  void runProgram()                      {L.runProgram();}
  Layout.Program startNewProgram()       {return L.startNewProgram();}
  void continueProgram(Layout.Program p) {L.continueProgram(p);}

  void createFreeChain()                                                        // Create the free chain
   {final Layout.Field index = btreeIndex();
    final Layout.Program p = L.startNewProgram();
    freeStartField.iWrite(1);
    for (int i = 1; i < size; i++)
     {final int I = i;
      L.P.new Instruction()
      {void action()
        {index.value = I;
         freeNextField.value = I+1;
         stuckIsFreeField.value = 1;
        }
      };
      freeNextField   .iWrite(freeNextField,    index);
      stuckIsFreeField.iWrite(stuckIsFreeField, index);
     }
    setRootAsLeaf();
    L.runProgram();
    L.continueProgram(p);
   }

  Stuck stuck()                                                                 // Make a temporary stuck we can copy into or out of as needed
   {return new Stuck(stuckSize, bitsPerKey, bitsPerData);
   }

  void copyStuckFrom(Stuck T, Layout.Field BtreeIndex)                          // Copy a stuck out of the btree
   {stuckSizeField.iRead(BtreeIndex);
    T.stuckSize.value = stuckSizeField.value;

    Layout.Field index = T.index();

    for (int i = 0; i < stuckSize; i++)
     {index.iWrite(i);
      stuckKeysField.iRead(BtreeIndex, index);
      T.stuckKeys.iWrite(T.stuckKeys,  index);
      stuckDataField.iRead(BtreeIndex, index);
      T.stuckData.iWrite(T.stuckData,  index);
     }
   }

//D1 Attributes                                                                 // Get and set attributes

  void setRootAsLeaf()                                                          // Set the root to be a leaf
   {final Layout.Field i = btreeIndex();
    final Layout.Field b = bit("isLeaf");
    i.value = 0;
    b.value = 1;
    stuckIsLeafField.iWrite(b, i);
   }

//D1 Find                                                                       // Find a key in a btree

  class IsLeaf
   {IsLeaf(Layout.Field index)                                                  // Process a stuck depending on wnether it is a leaf or a branch
     {stuckIsLeafField.iRead(index);
      final IsLeaf  l = this;
      L.P.new If(stuckIsLeafField)
       {void Then() {l.Leaf();}
        void Else() {l.Branch();}
       };
     }
    void Leaf() {}
    void Branch() {}
   }

//  public void find(Layout.Field Key)                                            // Find the leaf associated with a key in the tree
//   {L.P.new Block()
//     {void code()
//       {final Layout.Program.Label Return = end;
//        final Stuck        P = stuck();
//        final Layout.Field p = btreeIndex();
//
//        L.P.new Instruction()
//         {void action()
//           {p.value = 0;
//            copyStuckFrom(P, p);
//            stuckIsLeafField.iRead(p);
//            if (stuckIsLeafField.value > 0)
//             {P.find(Key);
//             }
//           }
//         };
//
//
//        L.P.new Block()                                                           // The root is a leaf
//         {void code()
//           {P.parallelStart();   P.GoOff(end, nT.isLeaf());                     // Confirm that the root is a leaf
//            P.parallelSection(); findEqualInLeaf(T.at(Key), nT);                // Assume the root is a leaf and start looking for the key
//            P.parallelEnd();
//
//            P.parallelStart();   T.at(find).zero();                             // Leaf that should contain this key is the root
//            P.parallelSection(); P.Goto(Return);
//            P.parallelEnd();
//           }
//         };
//
//        P.new Block()
//         {void code()
//           {findFirstGreaterThanOrEqualInBranch                                 // Find next child in search path of key
//             (nT, T.at(Key), null, null, T.at(child));
//            nT.loadNode(T.at(child));
//
//            P.new Block()                                                       // Found the containing leaf
//             {void code()
//               {P.parallelStart();   P.GoOff(end, nT.isLeaf());                 // Confirm that it is a leaf
//                P.parallelSection(); findEqualInLeaf(T.at(Key), nT);
//                P.parallelEnd();
//
//                P.parallelStart();   tt(find, child);
//                P.parallelSection(); P.Goto(Return);
//                P.parallelEnd();
//               }
//             };
//
//            P.Goto(start);                                                      // Restart search one level down
//           }
//         };
//       }
//     };
//   }

//D1 Print                                                                      // Print the tree

  public String toString()
   {final StringBuilder s = new StringBuilder();
    final Stuck t = stuck();
    final Layout.Field btreeIndex = btreeIndex();
    final Layout.Field stuckIndex = t.index();
    s.append("Btree\n");
    for (int i = 0; i < size; i++)
     {final Layout.Program p = L.startNewProgram();

      btreeIndex.iWrite(i);
      stuckIsLeafField.iRead(btreeIndex);
      stuckIsFreeField.iRead(btreeIndex);
      freeNextField   .iRead(btreeIndex);
      L.runProgram();
      L.continueProgram(p);

      if (stuckIsFreeField.value > 0) continue;                                 // Not in use as it is on the free chain
      s.append(String.format("Stuck: %2d   size: %d   free: %d   next: %2d  leaf: %d\n",
        i, t.stuckSize.value, freeNextField.value, freeNextField.value, stuckIsLeafField.value));

      copyStuckFrom(t, btreeIndex);                                             // Print stuck at this index in the btree
      s.append(""+t);
     }
    return ""+s;
   }

//D1 Tests                                                                      // Test the btree

  static Btree test_create()
   {final Btree b = new Btree(16, 4, 8, 8);
    ok(b, """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");
    return b;
   }

  static void test_leaf()
   {final Btree b = test_create();
    final Layout.Field btreeIndex = b.btreeIndex();
    final Layout.Field a          = b.variable("a", 4);

    btreeIndex.iWrite(0);
    b.new IsLeaf(btreeIndex)
     {void Leaf  () {a.iWrite(1);}
      void Branch() {a.iWrite(2);}
     };
    b.L.runProgram();
    ok(a, "a: value=1");
   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_create();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_leaf();
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
