
/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.dataflow.sdk.util;

import com.google.cloud.dataflow.sdk.coders.CannotProvideCoderException;
import com.google.cloud.dataflow.sdk.coders.Coder;
import com.google.cloud.dataflow.sdk.coders.CoderRegistry;
import com.google.cloud.dataflow.sdk.transforms.Combine.KeyedCombineFn;
import com.google.cloud.dataflow.sdk.transforms.CombineWithContext.Context;
import com.google.cloud.dataflow.sdk.transforms.CombineWithContext.KeyedCombineFnWithContext;
import com.google.cloud.dataflow.sdk.util.state.StateContext;

import java.io.IOException;
import java.io.NotSerializableException;
import java.io.ObjectOutputStream;

/**
 * Static utility methods that create combine function instances.
 */
public class CombineFnUtil {
  /**
   * Returns the partial application of the {@link KeyedCombineFnWithContext} to a specific
   * context to produce a {@link KeyedCombineFn}.
   *
   * <p>The returned {@link KeyedCombineFn} cannot be serialized.
   */
  public static <K, InputT, AccumT, OutputT> KeyedCombineFn<K, InputT, AccumT, OutputT>
  bindContext(
      KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn,
      StateContext<?> stateContext) {
    Context context = CombineContextFactory.createFromStateContext(stateContext);
    return new NonSerializableBoundedKeyedCombineFn<>(combineFn, context);
  }

  private static class NonSerializableBoundedKeyedCombineFn<K, InputT, AccumT, OutputT>
      extends KeyedCombineFn<K, InputT, AccumT, OutputT> {
    private final KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn;
    private final Context context;

    private NonSerializableBoundedKeyedCombineFn(
        KeyedCombineFnWithContext<K, InputT, AccumT, OutputT> combineFn,
        Context context) {
      this.combineFn = combineFn;
      this.context = context;
    }
    @Override
    public AccumT createAccumulator(K key) {
      return combineFn.createAccumulator(key, context);
    }
    @Override
    public AccumT addInput(K key, AccumT accumulator, InputT value) {
      return combineFn.addInput(key, accumulator, value, context);
    }
    @Override
    public AccumT mergeAccumulators(K key, Iterable<AccumT> accumulators) {
      return combineFn.mergeAccumulators(key, accumulators, context);
    }
    @Override
    public OutputT extractOutput(K key, AccumT accumulator) {
      return combineFn.extractOutput(key, accumulator, context);
    }
    @Override
    public AccumT compact(K key, AccumT accumulator) {
      return combineFn.compact(key, accumulator, context);
    }
    @Override
    public Coder<AccumT> getAccumulatorCoder(CoderRegistry registry, Coder<K> keyCoder,
        Coder<InputT> inputCoder) throws CannotProvideCoderException {
      return combineFn.getAccumulatorCoder(registry, keyCoder, inputCoder);
    }
    @Override
    public Coder<OutputT> getDefaultOutputCoder(CoderRegistry registry, Coder<K> keyCoder,
        Coder<InputT> inputCoder) throws CannotProvideCoderException {
      return combineFn.getDefaultOutputCoder(registry, keyCoder, inputCoder);
    }

    private void writeObject(@SuppressWarnings("unused") ObjectOutputStream out)
        throws IOException {
      throw new NotSerializableException(
          "Cannot serialize the CombineFn resulting from CombineFnUtil.bindContext.");
    }
  }
}
