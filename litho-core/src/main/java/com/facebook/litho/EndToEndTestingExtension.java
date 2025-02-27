/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.litho;

import android.graphics.Rect;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.facebook.rendercore.MountDelegateInput;
import com.facebook.rendercore.MountDelegateTarget;
import com.facebook.rendercore.RenderTreeNode;
import com.facebook.rendercore.extensions.ExtensionState;
import com.facebook.rendercore.extensions.MountExtension;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class EndToEndTestingExtension
    extends MountExtension<EndToEndTestingExtension.EndToEndTestingExtensionInput, Void> {

  // A map from test key to a list of one or more `TestItem`s which is only allocated
  // and populated during test runs.
  private final Map<String, Deque<TestItem>> mTestItemMap;
  private final MountDelegateTarget mMountDelegateTarget;
  private EndToEndTestingExtensionInput mInput;

  public interface EndToEndTestingExtensionInput extends MountDelegateInput {
    int getTestOutputCount();

    @Nullable
    TestOutput getTestOutputAt(int position);

    int getPositionForId(long id);

    RenderTreeNode getMountableOutputAt(int position);

    int getMountableOutputCount();
  }

  public EndToEndTestingExtension(MountDelegateTarget mountDelegateTarget) {
    mTestItemMap = new HashMap<String, Deque<TestItem>>();
    mMountDelegateTarget = mountDelegateTarget;
  }

  @Override
  protected Void createState() {
    return null;
  }

  @Override
  public void beforeMount(
      ExtensionState<Void> extensionState,
      EndToEndTestingExtensionInput input,
      Rect localVisibleRect) {
    mInput = input;
  }

  @Override
  public void afterMount(ExtensionState<Void> extensionState) {
    processTestOutputs();
  }

  @Override
  public void onVisibleBoundsChanged(ExtensionState<Void> extensionState, Rect localVisibleRect) {}

  @Override
  public void onUnmount(ExtensionState<Void> extensionState) {}

  @Override
  public void onUnbind(ExtensionState<Void> extensionState) {}

  private void processTestOutputs() {
    if (mTestItemMap == null) {
      return;
    }

    mTestItemMap.clear();

    for (int i = 0, size = mInput.getTestOutputCount(); i < size; i++) {
      final TestOutput testOutput = mInput.getTestOutputAt(i);
      final long layoutOutputId = testOutput.getLayoutOutputId();
      final TestItem testItem = new TestItem();
      testItem.setHost(getHost(testOutput));
      testItem.setBounds(testOutput.getBounds());
      testItem.setTestKey(testOutput.getTestKey());
      testItem.setContent(mMountDelegateTarget.getContentById(layoutOutputId));

      final Deque<TestItem> items = mTestItemMap.get(testOutput.getTestKey());
      final Deque<TestItem> updatedItems = items == null ? new LinkedList<TestItem>() : items;
      updatedItems.add(testItem);
      mTestItemMap.put(testOutput.getTestKey(), updatedItems);
    }
  }

  private @Nullable ComponentHost getHost(TestOutput testOutput) {
    for (int i = 0, size = mInput.getMountableOutputCount(); i < size; i++) {
      final RenderTreeNode renderTreeNode = mInput.getMountableOutputAt(i);
      if (renderTreeNode.getRenderUnit().getId() == testOutput.getLayoutOutputId()) {
        final RenderTreeNode hostTreeNode = renderTreeNode.getParent();
        if (hostTreeNode == null) {
          return null;
        }

        final ComponentHost host =
            (ComponentHost)
                mMountDelegateTarget.getContentById(hostTreeNode.getRenderUnit().getId());

        return host;
      }
    }

    return null;
  }

  /** @see LithoViewTestHelper#findTestItems(LithoView, String) */
  @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
  Deque<TestItem> findTestItems(String testKey) {
    if (mTestItemMap == null) {
      throw new UnsupportedOperationException(
          "Trying to access TestItems while "
              + "ComponentsConfiguration.isEndToEndTestRun is false.");
    }

    final Deque<TestItem> items = mTestItemMap.get(testKey);
    return items == null ? new LinkedList<TestItem>() : items;
  }
}
