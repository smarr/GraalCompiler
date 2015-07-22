/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
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
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#ifndef SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP
#define SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP

#include "ci/compilerInterface.hpp"

typedef void (*initializer)(void);

#if INCLUDE_JVMCI
// Per-compiler statistics
class CompilerStatistics VALUE_OBJ_CLASS_SPEC {
  friend class VMStructs;

  class Data VALUE_OBJ_CLASS_SPEC {
    friend class VMStructs;
  public:
    elapsedTimer _time;  // time spent compiling
    int _bytes;          // number of bytecodes compiled, including inlined bytecodes
    int _count;          // number of compilations
    Data() : _bytes(0), _count(0) {}
    void update(elapsedTimer time, int bytes) {
      _time.add(time);
      _bytes += bytes;
      _count++;
    }
    void reset() {
      _time.reset();
    }
  };

 public:
  Data _standard;  // stats for non-OSR compilations
  Data _osr;       // stats for OSR compilations
  int _nmethods_size; //
  int _nmethods_code_size;
  int bytes_per_second() {
    int bytes = _standard._bytes + _osr._bytes;
    if (bytes == 0) {
      return 0;
    }
    double seconds = _standard._time.seconds() + _osr._time.seconds();
    return seconds == 0.0 ? 0 : (int) (bytes / seconds);
  }
  CompilerStatistics() : _nmethods_size(0), _nmethods_code_size(0) {}
};
#endif

class AbstractCompiler : public CHeapObj<mtCompiler> {
 private:
  volatile int _num_compiler_threads;

 protected:
  volatile int _compiler_state;
  // Used for tracking global state of compiler runtime initialization
  enum { uninitialized, initializing, initialized, failed, shut_down };

  // This method returns true for the first compiler thread that reaches that methods.
  // This thread will initialize the compiler runtime.
  bool should_perform_init();

  // The (closed set) of concrete compiler classes. Using an tag like this
  // avoids a confusing use of macros around the definition of the
  // 'is_<compiler type>' methods.
  enum Type { c1, c2, shark, jvmci };

 private:
  Type _type;

#if INCLUDE_JVMCI
  CompilerStatistics _stats;
#endif

 public:
  AbstractCompiler(Type type) : _type(type), _compiler_state(uninitialized), _num_compiler_threads(0) {}

  // This function determines the compiler thread that will perform the
  // shutdown of the corresponding compiler runtime.
  bool should_perform_shutdown();

  // Name of this compiler
  virtual const char* name() = 0;

  // Should a native wrapper be generated by the runtime. This method
  // does *not* answer the question "can this compiler generate code for
  // a native method".
  virtual bool supports_native()                 { return true; }
  virtual bool supports_osr   ()                 { return true; }
  virtual bool can_compile_method(methodHandle method)  { return true; }
  bool is_c1   ()                                { return _type == c1; }
  bool is_c2   ()                                { return _type == c2; }
  bool is_shark()                                { return _type == shark; }
  bool is_jvmci()                                { return _type == jvmci; }

  // Customization
  virtual void initialize () = 0;

  void set_num_compiler_threads(int num) { _num_compiler_threads = num;  }
  int num_compiler_threads()             { return _num_compiler_threads; }

  // Get/set state of compiler objects
  bool is_initialized()           { return _compiler_state == initialized; }
  bool is_failed     ()           { return _compiler_state == failed;}
  void set_state     (int state);
  void set_shut_down ()           { set_state(shut_down); }
  // Compilation entry point for methods
  virtual void compile_method(ciEnv* env, ciMethod* target, int entry_bci) {
    ShouldNotReachHere();
  }


  // Print compilation timers and statistics
  virtual void print_timers() {
    ShouldNotReachHere();
  }

#if INCLUDE_JVMCI
  CompilerStatistics* stats() { return &_stats; }
#endif
};

#endif // SHARE_VM_COMPILER_ABSTRACTCOMPILER_HPP
