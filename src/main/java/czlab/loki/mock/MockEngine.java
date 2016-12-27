/* Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */

package czlab.loki.mock;

import czlab.loki.core.Engine;
import czlab.loki.core.Room;

/**
 * @author Kenneth Leung
 */
public class MockEngine implements Engine {

  /**
   */
  public MockEngine() {

  }

  @Override
  public void init(Object arg0) {
  }

  @Override
  public Object ready(Room room) {
    return null;
  }

  @Override
  public Object restart(Object arg) {
    return null;
  }

  @Override
  public Object start(Object arg) {
    return null;
  }

  @Override
  public void startRound(Object arg) {
  }

  @Override
  public void endRound(Object any) {
  }

  @Override
  public void stop() {
  }

  @Override
  public void update(Object event) {
  }

  @Override
  public Object state() {
    return null;
  }

  @Override
  public Object container() {
    return null;
  }

}


