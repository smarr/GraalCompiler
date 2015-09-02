/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
 */
package com.oracle.graal.compiler.common;

import static jdk.internal.jvmci.common.UnsafeAccess.*;

import java.util.*;

import jdk.internal.jvmci.common.*;
import sun.misc.*;

/**
 * Describes fields in a class, primarily for access via {@link Unsafe}.
 */
public class Fields {

    /**
     * Offsets used with {@link Unsafe} to access the fields.
     */
    protected final long[] offsets;

    /**
     * The names of the fields.
     */
    private final String[] names;

    /**
     * The types of the fields.
     */
    private final Class<?>[] types;

    private final Class<?>[] declaringClasses;

    public static Fields forClass(Class<?> clazz, Class<?> endClazz, boolean includeTransient, FieldsScanner.CalcOffset calcOffset) {
        FieldsScanner scanner = new FieldsScanner(calcOffset == null ? new FieldsScanner.DefaultCalcOffset() : calcOffset);
        scanner.scan(clazz, endClazz, includeTransient);
        return new Fields(scanner.data);
    }

    public Fields(ArrayList<? extends FieldsScanner.FieldInfo> fields) {
        Collections.sort(fields);
        this.offsets = new long[fields.size()];
        this.names = new String[offsets.length];
        this.types = new Class<?>[offsets.length];
        this.declaringClasses = new Class<?>[offsets.length];
        int index = 0;
        for (FieldsScanner.FieldInfo f : fields) {
            offsets[index] = f.offset;
            names[index] = f.name;
            types[index] = f.type;
            declaringClasses[index] = f.declaringClass;
            index++;
        }
    }

    /**
     * Gets the number of fields represented by this object.
     */
    public int getCount() {
        return offsets.length;
    }

    public static void translateInto(Fields fields, ArrayList<FieldsScanner.FieldInfo> infos) {
        for (int index = 0; index < fields.getCount(); index++) {
            infos.add(new FieldsScanner.FieldInfo(fields.offsets[index], fields.names[index], fields.types[index], fields.declaringClasses[index]));
        }
    }

    /**
     * Function enabling an object field value to be replaced with another value when being copied
     * within {@link Fields#copy(Object, Object, ObjectTransformer)}.
     */
    @FunctionalInterface
    public interface ObjectTransformer {
        Object apply(int index, Object from);
    }

    /**
     * Copies fields from {@code from} to {@code to}, both of which must be of the same type.
     *
     * @param from the object from which the fields should be copied
     * @param to the object to which the fields should be copied
     */
    public void copy(Object from, Object to) {
        copy(from, to, null);
    }

    /**
     * Copies fields from {@code from} to {@code to}, both of which must be of the same type.
     *
     * @param from the object from which the fields should be copied
     * @param to the object to which the fields should be copied
     * @param trans function to applied to object field values as they are copied. If {@code null},
     *            the value is copied unchanged.
     */
    public void copy(Object from, Object to, ObjectTransformer trans) {
        assert from.getClass() == to.getClass();
        for (int index = 0; index < offsets.length; index++) {
            long offset = offsets[index];
            Class<?> type = types[index];
            if (type.isPrimitive()) {
                if (type == Integer.TYPE) {
                    unsafe.putInt(to, offset, unsafe.getInt(from, offset));
                } else if (type == Long.TYPE) {
                    unsafe.putLong(to, offset, unsafe.getLong(from, offset));
                } else if (type == Boolean.TYPE) {
                    unsafe.putBoolean(to, offset, unsafe.getBoolean(from, offset));
                } else if (type == Float.TYPE) {
                    unsafe.putFloat(to, offset, unsafe.getFloat(from, offset));
                } else if (type == Double.TYPE) {
                    unsafe.putDouble(to, offset, unsafe.getDouble(from, offset));
                } else if (type == Short.TYPE) {
                    unsafe.putShort(to, offset, unsafe.getShort(from, offset));
                } else if (type == Character.TYPE) {
                    unsafe.putChar(to, offset, unsafe.getChar(from, offset));
                } else if (type == Byte.TYPE) {
                    unsafe.putByte(to, offset, unsafe.getByte(from, offset));
                } else {
                    assert false : "unhandled property type: " + type;
                }
            } else {
                Object obj = unsafe.getObject(from, offset);
                unsafe.putObject(to, offset, trans == null ? obj : trans.apply(index, obj));
            }
        }
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public Object get(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];
        Object value = null;
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                value = unsafe.getInt(object, offset);
            } else if (type == Long.TYPE) {
                value = unsafe.getLong(object, offset);
            } else if (type == Boolean.TYPE) {
                value = unsafe.getBoolean(object, offset);
            } else if (type == Float.TYPE) {
                value = unsafe.getFloat(object, offset);
            } else if (type == Double.TYPE) {
                value = unsafe.getDouble(object, offset);
            } else if (type == Short.TYPE) {
                value = unsafe.getShort(object, offset);
            } else if (type == Character.TYPE) {
                value = unsafe.getChar(object, offset);
            } else if (type == Byte.TYPE) {
                value = unsafe.getByte(object, offset);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            value = unsafe.getObject(object, offset);
        }
        return value;
    }

    /**
     * Gets the value of a field for a given object.
     *
     * @param object the object whose field is to be read
     * @param index the index of the field (between 0 and {@link #getCount()})
     * @return the value of the specified field which will be boxed if the field type is primitive
     */
    public long getRawPrimitive(Object object, int index) {
        long offset = offsets[index];
        Class<?> type = types[index];

        if (type == Integer.TYPE) {
            return unsafe.getInt(object, offset);
        } else if (type == Long.TYPE) {
            return unsafe.getLong(object, offset);
        } else if (type == Boolean.TYPE) {
            return unsafe.getBoolean(object, offset) ? 1 : 0;
        } else if (type == Float.TYPE) {
            return Float.floatToRawIntBits(unsafe.getFloat(object, offset));
        } else if (type == Double.TYPE) {
            return Double.doubleToRawLongBits(unsafe.getDouble(object, offset));
        } else if (type == Short.TYPE) {
            return unsafe.getShort(object, offset);
        } else if (type == Character.TYPE) {
            return unsafe.getChar(object, offset);
        } else if (type == Byte.TYPE) {
            return unsafe.getByte(object, offset);
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    /**
     * Determines if a field in the domain of this object is the same as the field denoted by the
     * same index in another {@link Fields} object.
     */
    public boolean isSame(Fields other, int index) {
        return other.offsets[index] == offsets[index];
    }

    public long[] getOffsets() {
        return offsets;
    }

    /**
     * Gets the name of a field.
     *
     * @param index index of a field
     */
    public String getName(int index) {
        return names[index];
    }

    /**
     * Gets the type of a field.
     *
     * @param index index of a field
     */
    public Class<?> getType(int index) {
        return types[index];
    }

    public Class<?> getDeclaringClass(int index) {
        return declaringClasses[index];
    }

    /**
     * Checks that a given field is assignable from a given value.
     *
     * @param index the index of the field to check
     * @param value a value that will be assigned to the field
     */
    private boolean checkAssignableFrom(Object object, int index, Object value) {
        assert value == null || getType(index).isAssignableFrom(value.getClass()) : String.format("Field %s.%s of type %s is not assignable from %s", object.getClass().getSimpleName(),
                        getName(index), getType(index).getSimpleName(), value.getClass().getSimpleName());
        return true;
    }

    public void set(Object object, int index, Object value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type.isPrimitive()) {
            if (type == Integer.TYPE) {
                unsafe.putInt(object, offset, (Integer) value);
            } else if (type == Long.TYPE) {
                unsafe.putLong(object, offset, (Long) value);
            } else if (type == Boolean.TYPE) {
                unsafe.putBoolean(object, offset, (Boolean) value);
            } else if (type == Float.TYPE) {
                unsafe.putFloat(object, offset, (Float) value);
            } else if (type == Double.TYPE) {
                unsafe.putDouble(object, offset, (Double) value);
            } else if (type == Short.TYPE) {
                unsafe.putShort(object, offset, (Short) value);
            } else if (type == Character.TYPE) {
                unsafe.putChar(object, offset, (Character) value);
            } else if (type == Byte.TYPE) {
                unsafe.putByte(object, offset, (Byte) value);
            } else {
                assert false : "unhandled property type: " + type;
            }
        } else {
            assert checkAssignableFrom(object, index, value);
            unsafe.putObject(object, offset, value);
        }
    }

    public void setRawPrimitive(Object object, int index, long value) {
        long offset = offsets[index];
        Class<?> type = types[index];
        if (type == Integer.TYPE) {
            unsafe.putInt(object, offset, (int) value);
        } else if (type == Long.TYPE) {
            unsafe.putLong(object, offset, value);
        } else if (type == Boolean.TYPE) {
            unsafe.putBoolean(object, offset, value != 0);
        } else if (type == Float.TYPE) {
            unsafe.putFloat(object, offset, Float.intBitsToFloat((int) value));
        } else if (type == Double.TYPE) {
            unsafe.putDouble(object, offset, Double.longBitsToDouble(value));
        } else if (type == Short.TYPE) {
            unsafe.putShort(object, offset, (short) value);
        } else if (type == Character.TYPE) {
            unsafe.putChar(object, offset, (char) value);
        } else if (type == Byte.TYPE) {
            unsafe.putByte(object, offset, (byte) value);
        } else {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(getClass().getSimpleName()).append('[');
        appendFields(sb);
        return sb.append(']').toString();
    }

    public void appendFields(StringBuilder sb) {
        for (int i = 0; i < offsets.length; i++) {
            sb.append(i == 0 ? "" : ", ").append(getName(i)).append('@').append(offsets[i]);
        }
    }

    public boolean getBoolean(Object n, int i) {
        assert types[i] == boolean.class;
        return unsafe.getBoolean(n, offsets[i]);
    }

    public byte getByte(Object n, int i) {
        assert types[i] == byte.class;
        return unsafe.getByte(n, offsets[i]);
    }

    public short getShort(Object n, int i) {
        assert types[i] == short.class;
        return unsafe.getShort(n, offsets[i]);
    }

    public char getChar(Object n, int i) {
        assert types[i] == char.class;
        return unsafe.getChar(n, offsets[i]);
    }

    public int getInt(Object n, int i) {
        assert types[i] == int.class;
        return unsafe.getInt(n, offsets[i]);
    }

    public long getLong(Object n, int i) {
        assert types[i] == long.class;
        return unsafe.getLong(n, offsets[i]);
    }

    public float getFloat(Object n, int i) {
        assert types[i] == float.class;
        return unsafe.getFloat(n, offsets[i]);
    }

    public double getDouble(Object n, int i) {
        assert types[i] == double.class;
        return unsafe.getDouble(n, offsets[i]);
    }

    public Object getObject(Object object, int i) {
        assert !types[i].isPrimitive();
        return unsafe.getObject(object, offsets[i]);
    }

    public void putObject(Object object, int i, Object value) {
        assert checkAssignableFrom(object, i, value);
        unsafe.putObject(object, offsets[i], value);
    }
}
