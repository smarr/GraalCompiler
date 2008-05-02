/*
 * Copyright 2001-2005 Sun Microsystems, Inc.  All Rights Reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Sun Microsystems, Inc., 4150 Network Circle, Santa Clara,
 * CA 95054 USA or visit www.sun.com if you need additional information or
 * have any questions.
 *
 */

package sun.jvm.hotspot.memory;

import java.util.*;
import sun.jvm.hotspot.debugger.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.types.*;
import sun.jvm.hotspot.types.OopField; // resolve ambiguity with oops.OopField

// following needed for on-the-fly field construction:
import sun.jvm.hotspot.types.basic.BasicOopField;
import sun.jvm.hotspot.types.basic.BasicTypeDataBase;

public class SystemDictionary {
  private static AddressField dictionaryField;
  private static AddressField sharedDictionaryField;
  private static AddressField placeholdersField;
  private static AddressField loaderConstraintTableField;
  private static OopField javaSystemLoaderField;
  private static int nofBuckets;

  private static OopField wellKnownKlasses;
  private static OopField objectKlassField;
  private static OopField classLoaderKlassField;
  private static OopField stringKlassField;
  private static OopField systemKlassField;
  private static OopField threadKlassField;
  private static OopField threadGroupKlassField;

  static {
    VM.registerVMInitializedObserver(new Observer() {
        public void update(Observable o, Object data) {
          initialize(VM.getVM().getTypeDataBase());
        }
      });
  }

  private static synchronized void initialize(TypeDataBase db) {
    Type type = db.lookupType("SystemDictionary");

    dictionaryField = type.getAddressField("_dictionary");
    sharedDictionaryField = type.getAddressField("_shared_dictionary");
    placeholdersField = type.getAddressField("_placeholders");
    loaderConstraintTableField = type.getAddressField("_loader_constraints");
    javaSystemLoaderField = type.getOopField("_java_system_loader");
    nofBuckets = db.lookupIntConstant("SystemDictionary::_nof_buckets").intValue();

    wellKnownKlasses = type.getOopField("_well_known_klasses[0]");
    objectKlassField = findWellKnownKlass("object_klass", type, db);
    classLoaderKlassField = findWellKnownKlass("classloader_klass", type, db);
    stringKlassField = findWellKnownKlass("string_klass", type, db);
    systemKlassField = findWellKnownKlass("system_klass", type, db);
    threadKlassField = findWellKnownKlass("thread_klass", type, db);
    threadGroupKlassField = findWellKnownKlass("threadGroup_klass", type, db);
  }

  private static OopField findWellKnownKlass(String indexName, Type type, TypeDataBase db) {
    Address wkk = wellKnownKlasses.getStaticFieldAddress();
    int index = db.lookupIntConstant("SystemDictionary::#"+indexName).intValue();
    return new BasicOopField((BasicTypeDataBase)db, type, indexName, type,
                             true, index * db.getAddressSize(), wkk);
  }

  public Dictionary dictionary() {
    Address tmp = dictionaryField.getValue();
    return (Dictionary) VMObjectFactory.newObject(Dictionary.class, tmp);
  }

  public Dictionary sharedDictionary() {
    Address tmp = sharedDictionaryField.getValue();
    return (Dictionary) VMObjectFactory.newObject(Dictionary.class, tmp);
  }

  public PlaceholderTable placeholders() {
    Address tmp = placeholdersField.getValue();
    return (PlaceholderTable) VMObjectFactory.newObject(PlaceholderTable.class, tmp);
  }

  public LoaderConstraintTable constraints() {
    Address tmp = placeholdersField.getValue();
    return (LoaderConstraintTable) VMObjectFactory.newObject(LoaderConstraintTable.class, tmp);
  }

  // few well known classes -- not all are added here.
  // add more if needed.
  public static InstanceKlass getThreadKlass() {
    return (InstanceKlass) newOop(threadKlassField.getValue());
  }

  public static InstanceKlass getThreadGroupKlass() {
    return (InstanceKlass) newOop(threadGroupKlassField.getValue());
  }

  public static InstanceKlass getObjectKlass() {
    return (InstanceKlass) newOop(objectKlassField.getValue());
  }

  public static InstanceKlass getStringKlass() {
    return (InstanceKlass) newOop(stringKlassField.getValue());
  }

  public static InstanceKlass getClassLoaderKlass() {
    return (InstanceKlass) newOop(classLoaderKlassField.getValue());
  }

  public static InstanceKlass getSystemKlass() {
    return (InstanceKlass) newOop(systemKlassField.getValue());
  }

  public InstanceKlass getAbstractOwnableSynchronizerKlass() {
    return (InstanceKlass) find("java/util/concurrent/locks/AbstractOwnableSynchronizer",
                                null, null);
  }

  public static Oop javaSystemLoader() {
    return newOop(javaSystemLoaderField.getValue());
  }

  public static int getNumOfBuckets() {
    return nofBuckets;
  }

  private static Oop newOop(OopHandle handle) {
    return VM.getVM().getObjectHeap().newOop(handle);
  }

  /** Lookup an already loaded class. If not found null is returned. */
  public Klass find(String className, Oop classLoader, Oop protectionDomain) {
    Symbol sym = VM.getVM().getSymbolTable().probe(className);
    if (sym == null) return null;
    return find(sym, classLoader, protectionDomain);
  }

  /** Lookup an already loaded class. If not found null is returned. */
  public Klass find(Symbol className, Oop classLoader, Oop protectionDomain) {
    Dictionary dict = dictionary();
    long hash = dict.computeHash(className, classLoader);
    int index = dict.hashToIndex(hash);
    return dict.find(index, hash, className, classLoader, protectionDomain);
  }

  /** Interface for iterating through all classes in dictionary */
  public static interface ClassVisitor {
    public void visit(Klass k);
  }

  /** Interface for iterating through all classes and their class
      loaders in dictionary */
  public static interface ClassAndLoaderVisitor {
    public void visit(Klass k, Oop loader);
  }

  /** Iterate over all klasses - including object, primitive
      array klasses */
  public void allClassesDo(final ClassVisitor v) {
    ClassVisitor visitor = new ClassVisitor() {
      public void visit(Klass k) {
        for (Klass l = k; l != null; l = l.arrayKlassOrNull()) {
          v.visit(l);
        }
      }
    };
    classesDo(visitor);
    VM.getVM().getUniverse().basicTypeClassesDo(visitor);
  }

  /** Iterate over all klasses in dictionary; just the classes from
      declaring class loaders */
  public void classesDo(ClassVisitor v) {
    dictionary().classesDo(v);
  }

  /** All classes, and their class loaders */
  public void classesDo(ClassAndLoaderVisitor v) {
    dictionary().classesDo(v);
  }

  /** All array classes of primitive type, and their class loaders */
  public void primArrayClassesDo(ClassAndLoaderVisitor v) {
    placeholders().primArrayClassesDo(v);
  }
}
