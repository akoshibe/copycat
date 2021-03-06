/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.kuujo.copycat.protocol.rpc;

import java.nio.ByteBuffer;
import java.util.Objects;

/**
 * Protocol commit response.
 *
 * @author <a href="http://github.com/kuujo">Jordan Halterman</a>
 */
public class CommitResponse extends AbstractResponse {

  /**
   * Returns a new commit response builder.
   *
   * @return A new commit response builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns a commit response builder for an existing response.
   *
   * @param response The response to build.
   * @return The commit response builder.
   */
  public static Builder builder(CommitResponse response) {
    return new Builder(response);
  }

  private ByteBuffer result;

  /**
   * Returns the commit result.
   *
   * @return The commit result.
   */
  public ByteBuffer result() {
    return result;
  }

  @Override
  public int hashCode() {
    return Objects.hash(member, status, result);
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof CommitResponse) {
      CommitResponse response = (CommitResponse) object;
      return response.member.equals(member)
        && response.status == status
        && ((response.result == null && result == null)
        || response.result != null && result != null && response.result.equals(result));
    }
    return false;
  }

  @Override
  public String toString() {
    return String.format("%s[status=%s, result=%s]", getClass().getSimpleName(), status, result);
  }

  /**
   * Commit response builder.
   */
  public static class Builder extends AbstractResponse.Builder<Builder, CommitResponse> {
    private Builder() {
      this(new CommitResponse());
    }

    private Builder(CommitResponse response) {
      super(response);
    }

    /**
     * Sets the commit response result.
     *
     * @param result The response result.
     * @return The response builder.
     */
    public Builder withResult(ByteBuffer result) {
      response.result = result;
      return this;
    }

    @Override
    public int hashCode() {
      return Objects.hash(response);
    }

    @Override
    public boolean equals(Object object) {
      return object instanceof Builder && ((Builder) object).response.equals(response);
    }

    @Override
    public String toString() {
      return String.format("%s[response=%s]", getClass().getCanonicalName(), response);
    }

  }

}
