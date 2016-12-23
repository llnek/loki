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

package czlab.loki.core;

import czlab.xlib.Identifiable;
import czlab.xlib.Hierarchial;
import czlab.xlib.Receivable;
import czlab.xlib.Sendable;
import java.io.Closeable;

/**
 * @author Kenneth Leung
 */
public interface Session extends Closeable
                                 ,Sendable
                                 ,Receivable
                                 ,Hierarchial
                                 ,Identifiable {

  /**
   */
  public void setStatus(int status);

  /**
   */
  public int status();

  /**
   */
  public boolean isShuttingDown();

  /**
   */
  public boolean isConnected();

  /**
   */
  public void bind(Object impl);

  /**
   */
  public Room room();

  /**
   */
  public Player player();

  /**
   */
  public long number();

}


