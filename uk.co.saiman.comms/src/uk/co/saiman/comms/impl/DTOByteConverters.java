/*
 * Copyright (C) 2017 Scientific Analysis Instruments Limited <contact@saiman.co.uk>
 *          ______         ___      ___________
 *       ,'========\     ,'===\    /========== \
 *      /== \___/== \  ,'==.== \   \__/== \___\/
 *     /==_/____\__\/,'==__|== |     /==  /
 *     \========`. ,'========= |    /==  /
 *   ___`-___)== ,'== \____|== |   /==  /
 *  /== \__.-==,'==  ,'    |== '__/==  /_
 *  \======== /==  ,'      |== ========= \
 *   \_____\.-\__\/        \__\\________\/
 *
 * This file is part of uk.co.saiman.comms.
 *
 * uk.co.saiman.comms is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * uk.co.saiman.comms is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package uk.co.saiman.comms.impl;

import static java.util.stream.Collectors.toList;
import static org.osgi.service.component.annotations.ReferenceCardinality.MULTIPLE;
import static org.osgi.service.component.annotations.ReferencePolicyOption.GREEDY;
import static uk.co.strangeskies.collection.stream.StreamUtilities.throwingMerger;
import static uk.co.strangeskies.reflection.Types.unwrapPrimitive;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import uk.co.saiman.comms.BitArray;
import uk.co.saiman.comms.BitConverter;
import uk.co.saiman.comms.Bits;
import uk.co.saiman.comms.BitsConversion;
import uk.co.saiman.comms.BitsElements;
import uk.co.saiman.comms.ByteConverter;
import uk.co.saiman.comms.ByteConverters;
import uk.co.saiman.comms.Bytes;
import uk.co.saiman.comms.CommsException;
import uk.co.strangeskies.collection.stream.StreamUtilities;

@Component
public class DTOByteConverters implements ByteConverters {
	private Map<Class<?>, DTOByteConverter<?>> byteConverters = Collections
			.synchronizedMap(new HashMap<>());

	@Override
	@SuppressWarnings("unchecked")
	public <T> DTOByteConverter<T> getConverter(Class<T> type) {
		return (DTOByteConverter<T>) byteConverters.computeIfAbsent(type, DTOByteConverter::new);
	}

	@Reference(cardinality = MULTIPLE, policyOption = GREEDY)
	private List<BitConverter<?>> bitConverters = new CopyOnWriteArrayList<>();

	class DTOByteConverter<T> implements ByteConverter<T> {
		private class FieldBitConverter {
			private final BitConverter<Object> converter;
			private final int converterPosition;
			private final int converterSize;

			@SuppressWarnings("unchecked")
			public FieldBitConverter(
					BitConverter<?> converter,
					int converterPosition,
					int converterSize) {
				if (converterSize < 0)
					converterSize = converter.getDefaultBits();

				this.converter = (BitConverter<Object>) converter;
				this.converterPosition = converterPosition;
				this.converterSize = converterSize;
			}

			public BitArray toBits(BitArray bitSet, Object object) {
				bitSet = bitSet.splice(converterPosition, converter.toBits(object, converterSize));
				return bitSet;
			}

			public Object toObject(BitArray bitSet) {
				return converter.toObject(bitSet.trim(-converterPosition).resize(converterSize));
			}

			public int getPosition() {
				return converterPosition;
			}

			public int getSize() {
				return converterSize;
			}
		}

		private final Class<T> type;
		private final Map<Field, List<FieldBitConverter>> fieldConverters;
		private final int bits;
		private final int bytes;

		DTOByteConverter(Class<T> type) {
			this.type = type;
			this.fieldConverters = new HashMap<>();

			for (Field field : type.getFields()) {
				List<FieldBitConverter> converters = createConverters(field);
				fieldConverters.put(field, converters);
			}

			int maxBit = getFields()
					.flatMap(this::getFieldConverters)
					.mapToInt(c -> c.getPosition() + c.getSize())
					.max()
					.orElse(0);

			int maxByte = (int) Math.ceil(maxBit / (double) Byte.SIZE);

			if (type.isAnnotationPresent(Bytes.class)) {
				int specifiedBytes = type.getAnnotation(Bytes.class).count();
				if (specifiedBytes < maxByte) {
					throw new CommsException(
							"Converter for " + type + " exceeds specified byte count " + specifiedBytes);
				} else {
					maxByte = specifiedBytes;
				}
			}

			this.bits = maxBit;
			this.bytes = maxByte;
		}

		public int getByteCount() {
			return bytes;
		}

		public int getBitCount() {
			return bits;
		}

		public Stream<Field> getFields() {
			return fieldConverters.keySet().stream();
		}

		/*
		 * Each field has a list of converters, if the field is an array, they
		 * represent the elements in order. If the field is not an array, the stream
		 * should only contain one item.
		 */
		public Stream<FieldBitConverter> getFieldConverters(Field field) {
			return fieldConverters.get(field).stream();
		}

		@Override
		public T create() {
			T object;
			try {
				object = type.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				throw new RuntimeException(e);
			}

			getFields().filter(field -> field.getType().isArray()).forEach(field -> {
				Object value = Array.newInstance(
						field.getType().getComponentType(),
						(int) getFieldConverters(field).count());

				try {
					field.set(object, value);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			});

			return object;
		}

		@Override
		public T fromBytes(byte[] bytes) {
			if (bytes.length < getByteCount()) {
				throw new CommsException(
						"Not enough bytes for conversion to " + type + ", " + bytes.length);
			}

			BitArray bitSet = BitArray.fromByteArray(bytes);

			T object = create();

			getFields().forEach(field -> {
				List<Object> elements = getFieldConverters(field)
						.map(converter -> converter.toObject(bitSet))
						.collect(toList());

				Object value;
				if (field.getType().isArray()) {
					value = Array.newInstance(field.getType().getComponentType(), elements.size());
					for (int i = 0; i < elements.size(); i++)
						Array.set(value, i, elements.get(i));
				} else {
					value = elements.get(0);
				}

				try {
					field.set(object, value);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
			});

			return object;
		}

		@Override
		public byte[] toBytes(T object) {
			BitArray bitSet = new BitArray(getByteCount() * Byte.SIZE);

			bitSet = getFields().flatMap(field -> {
				Object value;
				try {
					value = field.get(object);
				} catch (IllegalArgumentException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}

				List<Object> elements;
				if (field.getType().isArray()) {
					elements = new ArrayList<>();
					for (int i = 0; i < Array.getLength(value); i++) {
						elements.add(Array.get(value, i));
					}
				} else {
					elements = Arrays.asList(value);
				}

				return StreamUtilities.zip(elements.stream(), getFieldConverters(field));

			}).reduce(bitSet, (b, e) -> e.getValue().toBits(b, e.getKey()), throwingMerger());

			return Arrays.copyOf(bitSet.toByteArray(), getByteCount());
		}

		private List<FieldBitConverter> createConverters(Field field) {
			Type type = field.getGenericType();

			BitsConversion conversion = field.getAnnotation(BitsConversion.class);

			if (field.isAnnotationPresent(BitsElements.class)) {
				return Arrays
						.stream(field.getAnnotation(BitsElements.class).value())
						.map(b -> createElementConverter(getElementType(type), b, conversion))
						.collect(toList());
			} else {
				return Arrays
						.asList(createElementConverter(type, field.getAnnotation(Bits.class), conversion));
			}
		}

		private Type getElementType(Type type) {
			if (type instanceof Class<?>) {
				return ((Class<?>) type).getComponentType();
			} else if (type instanceof GenericArrayType) {
				return ((GenericArrayType) type).getGenericComponentType();
			}
			throw new CommsException("Field with multiple bits annotions must be array type");
		}

		private FieldBitConverter createElementConverter(
				Type type,
				Bits bits,
				BitsConversion conversion) {
			@SuppressWarnings("rawtypes")
			Class<? extends BitConverter> converterClass = (bits
					.conversion()
					.converter() != BitConverter.class)
							? converterClass = bits.conversion().converter()
							: conversion != null ? conversion.converter() : BitConverter.class;
			int converterPosition = bits.value();
			int converterSize = (bits.conversion().size() >= 0)
					? bits.conversion().size()
					: conversion != null ? conversion.size() : -1;

			Stream<BitConverter<?>> converterStream;
			BitConverter<?> converter;
			if (converterClass != BitConverter.class) {
				try {
					converter = converterClass.newInstance();
				} catch (InstantiationException | IllegalAccessException e) {
					throw new RuntimeException(e);
				}
				converterStream = Stream.of(converter);
			} else {
				converterStream = bitConverters.stream();
			}

			return converterStream
					.filter(c -> unwrapPrimitive(c.getType()) == unwrapPrimitive(type))
					.map(c -> new FieldBitConverter(c, converterPosition, converterSize))
					.findFirst()
					.get();
		}
	}
}
