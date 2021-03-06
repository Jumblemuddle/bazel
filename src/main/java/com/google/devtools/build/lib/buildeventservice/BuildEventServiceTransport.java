// Copyright 2017 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.buildeventservice;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.eventbus.EventBus;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import com.google.devtools.build.lib.buildeventservice.client.BuildEventServiceClient;
import com.google.devtools.build.lib.buildeventstream.ArtifactGroupNamer;
import com.google.devtools.build.lib.buildeventstream.BuildEvent;
import com.google.devtools.build.lib.buildeventstream.BuildEventArtifactUploader;
import com.google.devtools.build.lib.buildeventstream.BuildEventProtocolOptions;
import com.google.devtools.build.lib.buildeventstream.BuildEventTransport;
import com.google.devtools.build.lib.clock.Clock;
import com.google.devtools.build.lib.util.JavaSleeper;
import com.google.devtools.build.lib.util.Sleeper;
import java.time.Duration;
import javax.annotation.Nullable;

/** A {@link BuildEventTransport} that streams {@link BuildEvent}s to BuildEventService. */
public class BuildEventServiceTransport implements BuildEventTransport {
  private final BuildEventServiceUploader besUploader;

  private BuildEventServiceTransport(
      BuildEventServiceClient besClient,
      BuildEventArtifactUploader localFileUploader,
      BuildEventProtocolOptions bepOptions,
      BuildEventServiceProtoUtil besProtoUtil,
      Clock clock,
      ExitFunction exitFunc,
      boolean publishLifecycleEvents,
      ArtifactGroupNamer artifactGroupNamer,
      EventBus eventBus,
      Duration closeTimeout,
      Sleeper sleeper) {
    this.besUploader =
        new BuildEventServiceUploader.Builder()
            .besClient(besClient)
            .localFileUploader(localFileUploader)
            .bepOptions(bepOptions)
            .besProtoUtil(besProtoUtil)
            .clock(clock)
            .exitFunc(exitFunc)
            .publishLifecycleEvents(publishLifecycleEvents)
            .sleeper(sleeper)
            .artifactGroupNamer(artifactGroupNamer)
            .eventBus(eventBus)
            .closeTimeout(closeTimeout)
            .build();
  }

  @Override
  public ListenableFuture<Void> close() {
    // This future completes once the upload has finished. As
    // per API contract it is expected to never fail.
    SettableFuture<Void> closeFuture = SettableFuture.create();
    ListenableFuture<Void> uploaderCloseFuture = besUploader.close();
    uploaderCloseFuture.addListener(() -> closeFuture.set(null), MoreExecutors.directExecutor());
    return closeFuture;
  }

  @Override
  public BuildEventArtifactUploader getUploader() {
    return besUploader.getLocalFileUploader();
  }

  @Override
  public String name() {
    return "Build Event Service";
  }

  @Override
  public void sendBuildEvent(BuildEvent event) {
    besUploader.enqueueEvent(event);
  }

  @VisibleForTesting
  public BuildEventServiceUploader getBesUploader() {
    return besUploader;
  }

  /** A builder for {@link BuildEventServiceTransport}. */
  public static class Builder {
    private BuildEventServiceClient besClient;
    private BuildEventArtifactUploader localFileUploader;
    private BuildEventServiceOptions besOptions;
    private BuildEventProtocolOptions bepOptions;
    private Clock clock;
    private ArtifactGroupNamer artifactGroupNamer;
    private BuildEventServiceProtoUtil besProtoUtil;
    private EventBus eventBus;
    private ExitFunction exitFunction;
    private @Nullable Sleeper sleeper;

    public Builder besClient(BuildEventServiceClient value) {
      this.besClient = value;
      return this;
    }

    public Builder localFileUploader(BuildEventArtifactUploader value) {
      this.localFileUploader = value;
      return this;
    }

    public Builder besProtoUtil(BuildEventServiceProtoUtil value) {
      this.besProtoUtil = value;
      return this;
    }

    public Builder bepOptions(BuildEventProtocolOptions value) {
      this.bepOptions = value;
      return this;
    }

    public Builder besOptions(BuildEventServiceOptions value) {
      this.besOptions = value;
      return this;
    }

    public Builder clock(Clock value) {
      this.clock = value;
      return this;
    }

    public Builder artifactGroupNamer(ArtifactGroupNamer value) {
      this.artifactGroupNamer = value;
      return this;
    }

    public Builder eventBus(EventBus value) {
      this.eventBus = value;
      return this;
    }

    public Builder exitFunction(ExitFunction value) {
      this.exitFunction = value;
      return this;
    }

    @VisibleForTesting
    public Builder sleeper(Sleeper value) {
      this.sleeper = value;
      return this;
    }

    public BuildEventServiceTransport build() {
      checkNotNull(besOptions);
      return new BuildEventServiceTransport(
          checkNotNull(besClient),
          checkNotNull(localFileUploader),
          checkNotNull(bepOptions),
          checkNotNull(besProtoUtil),
          checkNotNull(clock),
          checkNotNull(exitFunction),
          besOptions.besLifecycleEvents,
          checkNotNull(artifactGroupNamer),
          checkNotNull(eventBus),
          (besOptions.besTimeout != null) ? besOptions.besTimeout : Duration.ZERO,
          sleeper != null ? sleeper : new JavaSleeper());
    }
  }
}
