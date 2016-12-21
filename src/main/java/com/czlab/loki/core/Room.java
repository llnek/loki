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

import czlab.loki.event.Receiver;
import czlab.loki.event.Sender;
import czlab.xlib.Dispatchable;
import czlab.xlib.Identifiable;
import java.io.Closeable;


/**
 * @author Kenneth Leung
 */
public interface Room extends Identifiable, Sender, Receiver, Dispatchable, Closeable {

  /**
   */
  public void broadcast(Object networkEvent);

  /**
   */
  public void disconnect(Session s);

  /**
   */
  public Session connect(Player p);

  /**
   */
  public GameEngine engine();

  /**
   */
  public Game game();

  /**
   */
  public int countPlayers();

  /**
   */
  public boolean isShuttingDown();

  /**
   */
  public boolean canActivate();

  /**
   */
  public void activate();

  /**
   */
  public boolean isActive();

  /**
   */
  public Object gist();

}


