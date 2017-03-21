/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.loki.sys;

import czlab.jasal.Dispatchable;
import czlab.jasal.Identifiable;
import czlab.jasal.Receivable;
import czlab.jasal.Sendable;
import java.io.Closeable;

/**
 * @author Kenneth Leung
 */
public interface Room extends Identifiable
                              , Sendable
                              , Receivable
                              , Dispatchable
                              , Closeable {

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
  public int countPlayers();

  /**
   */
  public boolean isShuttingDown();

  /**
   */
  public boolean canOpen();

  /**
   */
  public void open();

  /**
   */
  public Object gist();

}


