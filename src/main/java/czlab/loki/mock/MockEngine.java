/**
 * Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
 * The use and distribution terms for this software are covered by the
 * Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
 * which can be found in the file epl-v10.html at the root of this distribution.
 * By using this software in any fashion, you are agreeing to be bound by
 * the terms of this license.
 * You must not remove this notice, or any other, from this software.
 */

package czlab.loki.mock;

import czlab.loki.game.GameRoom;
import czlab.loki.game.Engine;

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
  public Object ready(GameRoom room) {
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
  public GameRoom container() {
    return null;
  }

}

