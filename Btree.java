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
  void clearProgram()                    {L.clearProgram();}
  void stopProgram(String message)       {L.stopProgram(message);}
  Layout.Program startNewProgram()       {return L.startNewProgram();}
  void continueProgram(Layout.Program p) {L.continueProgram(p);}

//D2 Allocation                                                                 // Allocate stucks from the free chain

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
      freeNextField   .iWrite(index);
      stuckIsFreeField.iWrite(index);
     }
    setRootAsLeaf();
    L.runProgram();
    L.continueProgram(p);
   }

  private void allocate(Layout.Field ref)                                       // Allocate a stuck and set a ref to the allocated node
   {final Layout.Field btreeIndex = btreeIndex();
    L.P.new Instruction()
     {void action()
       {if (freeStartField.value == 0) stopProgram("Out of memory");            // Check memory
        ref.value = btreeIndex.value = freeStartField.value;                    // Head of free chain gives allocated stuck
       }
     };

    stuckIsFreeField.iZero(btreeIndex);                                         // Show as in use
L.P.new Instruction()
 {void action()
   {
say("FFFF", bTreeIndex, stuckIsFreeField);
   }
 };

    freeNextField.iRead(btreeIndex);                                            // Locate next stuck on free chain to become new first stuck on free chain

    L.P.new Instruction()
     {void action()
       {freeStartField.value = freeNextField.value;                             // Next stuck on free chain becomes head of free chain
       }
     };
    freeNextField.iZero(btreeIndex);                                            // Clear the next field from the current stuck
   }

  private void free(Layout.Field ref)                                           // Free the indicated stuck to make it available for reuse
   {L.P.new Instruction()
     {void action()
       {if (ref.value == 0)                                                     // The root stuck cannot be freed
         {stopProgram("Cannot free the root stuck");
          return;
         }
       }
     };
    L.P.new Instruction()
     {void action()
       {freeNextField.value = freeStartField.value;
       }
     };

    freeNextField.iWrite(ref);                                                  // Append the free chain to this stuck
    L.P.new Instruction()
     {void action()
       {freeStartField.value = ref.value;                                       // This stuck becomes the first stuick on the free chain
       }
     };
    stuckIsFreeField.iOne(ref);                                                 // Show as free
   }

//D2 Stuck                                                                      // Get and set stucks within btree

  Stuck stuck()                                                                 // Make a temporary stuck we can copy into or out of as needed
   {final Stuck s = new Stuck(stuckSize, bitsPerKey, bitsPerData);
    s.L.P = L.P;
    return s;
   }

  void copyStuckFrom(Stuck T, Layout.Field BtreeIndex)                          // Copy a stuck out of the btree
   {stuckSizeField.iRead(BtreeIndex);
    L.P.new Instruction()
     {void action()
       {T.stuckSize.value = stuckSizeField.value;
say("AAAA", BtreeIndex);
       }
     };

    Layout.Field index = T.index();

    for (int i = 0; i < T.size; i++)
     {index.iWrite(i);
      stuckKeysField.iRead(BtreeIndex, index); L.P.new Instruction() {void action() {T.stuckKeys.value = stuckKeysField.value;}}; T.stuckKeys.iWrite(index);
      L.P.new Instruction()
      {void action()
        {say("TTTT", BtreeIndex, index, stuckKeysField);
        }
      };
      stuckDataField.iRead(BtreeIndex, index); L.P.new Instruction() {void action() {T.stuckData.value = stuckDataField.value;}}; T.stuckData.iWrite(index);
     }
   }

  void saveStuckInto(Stuck T, Layout.Field BtreeIndex)                          // Save a stuck into the indocated position in the btree
   {stuckSizeField.iWrite(T.stuckSize.value);

    Layout.Field index = T.index();

    for (int i = 0; i < T.size; i++)
     {final int I = i;
      index.iWrite(I);
      T.stuckKeys.iRead(index); L.P.new Instruction() {void action() {stuckKeysField.value = T.stuckKeys.value;}}; stuckKeysField.iWrite(BtreeIndex, index);
      T.stuckData.iRead(index); L.P.new Instruction() {void action() {stuckDataField.value = T.stuckData.value;}}; stuckDataField.iWrite(BtreeIndex, index);
     }
   }

//D1 Attributes                                                                 // Get and set attributes

  void setRootAsLeaf()                                                          // Set the root to be a leaf
   {final Layout.Field i = btreeIndex();
    i.iWrite(0);
    stuckIsLeafField.iOne(i);
   }

  void setRootAsBranch()                                                        // Set the root to be a branch
   {final Layout.Field i = btreeIndex();
    i.iWrite(1);
    stuckIsLeafField.iOne(i);
   }

  void setLeaf  (Layout.Field i) {stuckIsLeafField.iOne (i);}                   // Set a stuck in the btree to be a leaf
  void setBranch(Layout.Field i) {stuckIsLeafField.iZero(i);}                   // Set a stuck in the btree to be a branch

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


      stuckIsLeafField.iRead(btreeIndex);
      stuckIsFreeField.iRead(btreeIndex);
      freeNextField   .iRead(btreeIndex);

      btreeIndex.iWrite(i);
L.P.new Instruction()
 {void action()
   {say("BBBB", btreeIndex, stuckIsFreeField.value);
   }
 };
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
    b.runProgram();
    ok(a, "a: value=1");
   }

  static void test_allocFree()
   {final Btree b = test_create();
    final Layout.Field x = b.btreeIndex();
    final Layout.Field y = b.btreeIndex();

    ok(b.freeStartField, "freeStart: value=1");
    ok(b.freeNextField,  "freeNext: value=0, 0=0, 1=2, 2=3, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=0");
    ok(b, """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.allocate(x);
    b.allocate(y);
    b.runProgram();

    ok(x, "btreeIndex: value=1");
    ok(y, "btreeIndex: value=2");

    ok(b.freeStartField, "freeStart: value=3");
    ok(b.freeNextField,  "freeNext: value=0, 0=0, 1=0, 2=0, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=0");

    ok(b, """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  1   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  2   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

    b.clearProgram();
    b.free(x);
    b.free(y);
    b.runProgram();
    ok(b.freeStartField, "freeStart: value=2");
    ok(b.freeNextField,  "freeNext: value=1, 0=0, 1=3, 2=1, 3=4, 4=5, 5=6, 6=7, 7=8, 8=9, 9=10, 10=11, 11=12, 12=13, 13=14, 14=15, 15=0");
    ok(b, """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");
   }

  static void test_btree()
   {final Btree b = test_create();
    final Layout.Field s = b.btreeIndex();
    final Layout.Field t = b.btreeIndex();
    final Layout.Field x = b.btreeIndex();
    final Layout.Field y = b.btreeIndex();

    final Stuck S = b.stuck();
    final Stuck T = b.stuck();
    final Stuck X = b.stuck();
    final Stuck Y = b.stuck();
    final Stuck Z = b.stuck();

    b.allocate(s);
    b.allocate(t);
    b.allocate(x);
    b.allocate(y);
    b.runProgram();

    b.clearProgram();
    S.stuckKeys.iWrite( 1); S.stuckData.iWrite( 2); S.push();
    S.stuckKeys.iWrite( 2); S.stuckData.iWrite( 4); S.push();
    S.stuckKeys.iWrite( 3); S.stuckData.iWrite( 6); S.push();
    S.stuckKeys.iWrite( 4); S.stuckData.iWrite( 8); S.push();
    b.runProgram();

    b.clearProgram();
    T.stuckKeys.iWrite(11); T.stuckData.iWrite(12); T.push();
    T.stuckKeys.iWrite(12); T.stuckData.iWrite(14); T.push();
    T.stuckKeys.iWrite(13); T.stuckData.iWrite(16); T.push();
    T.stuckKeys.iWrite(14); T.stuckData.iWrite(18); T.push();
    b.runProgram();

    b.clearProgram();
    X.stuckKeys.iWrite(21); X.stuckData.iWrite(22); X.push();
    X.stuckKeys.iWrite(22); X.stuckData.iWrite(24); X.push();
    X.stuckKeys.iWrite(23); X.stuckData.iWrite(26); X.push();
    X.stuckKeys.iWrite(24); X.stuckData.iWrite(28); X.push();
    b.runProgram();

    b.clearProgram();
    Y.stuckKeys.iWrite(31); Y.stuckData.iWrite(32); Y.push();
    Y.stuckKeys.iWrite(32); Y.stuckData.iWrite(34); Y.push();
    Y.stuckKeys.iWrite(33); Y.stuckData.iWrite(36); Y.push();
    Y.stuckKeys.iWrite(34); Y.stuckData.iWrite(38); Y.push();
    b.runProgram();

    b.clearProgram();
    Z.stuckKeys.iWrite(10); Z.stuckData.iWrite(s.value); Z.push();
    Z.stuckKeys.iWrite(20); Z.stuckData.iWrite(t.value); Z.push();
    Z.stuckKeys.iWrite(30); Z.stuckData.iWrite(x.value); Z.push();
    Z.stuckKeys.iWrite(40); Z.stuckData.iWrite(y.value); Z.push();
    b.runProgram();

    b.clearProgram();
    b.saveStuckInto(S, s);
    b.saveStuckInto(T, t);
    b.saveStuckInto(X, x);
    b.saveStuckInto(Y, y);
    b.runProgram();
say("CCCC", b.stuckKeysField);
    ok(b, """
Btree
Stuck:  0   size: 0   free: 0   next:  0  leaf: 1
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  1   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  2   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  3   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
Stuck:  4   size: 0   free: 0   next:  0  leaf: 0
stuckSize: value=0
stuckKeys: value=0, 0=0, 1=0, 2=0, 3=0
stuckData: value=0, 0=0, 1=0, 2=0, 3=0
""");

   }

  static void oldTests()                                                        // Tests thought to be in good shape
   {test_create();
    test_leaf();
    test_allocFree();
   }

  static void newTests()                                                        // Tests being worked on
   {//oldTests();
    test_btree();
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
