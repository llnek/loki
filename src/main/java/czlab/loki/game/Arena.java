/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.loki.game;

import czlab.loki.core.Session;
import czlab.jasal.Restartable;
import czlab.jasal.Disposable;
import czlab.jasal.Startable;
import czlab.jasal.Initable;

/**
 * @author kenl
 */
public interface Arena extends Initable, Startable, Restartable, Disposable {

  //life cycle of engine
  //1. initialize
  //2. ready
  //3. start/restart
  public Object ready(GameRoom room);

  /**
   */
  public void update(Object event);

  /**
   */
  public Object state();

  /**
   */
  public GameRoom container();

}


