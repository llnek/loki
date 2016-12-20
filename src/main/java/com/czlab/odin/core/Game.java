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

package czlab.odin.core;

import czlab.xlib.Identifiable;
import czlab.xlib.Nameable;

/**
 * @author Kenneth Leung
 */
public interface Game extends Identifiable, Nameable {

  /**
   */
  public boolean supportMultiPlayers();

  /**
   */
  public int maxPlayers();

  /**
   */
  public int minPlayers();

  /**
   */
  public String engineClass();

  /**
   */
  public Object gist();

  /**
   */
  public Object unload();

}

