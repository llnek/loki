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

package czlab.loki.event;

/**
 * @author Kenneth Leung
 */
public interface Events {

  // Event types
  public static final long GLOBAL        = 1L;
  public static final long LOCAL         = 2L;
  public static final long UNIT          = 3L;

  // msg code
  public static final long PLAYREQ_NOK        = 100L;
  public static final long JOINREQ_NOK        = 101L;
  public static final long USER_NOK           = 102L;
  public static final long GAME_NOK           = 103L;
  public static final long ROOM_NOK           = 104L;
  public static final long ROOM_FILLED        = 105L;
  public static final long ROOMS_FULL         = 106L;

  public static final long PLAYREQ_OK         = 200L;
  public static final long JOINREQ_OK         = 201L;

  public static final long PLAYER_JOINED      = 300L;
  public static final long STARTED            = 301L;
  public static final long CONNECTED          = 302L;
  public static final long ERROR              = 303L;
  public static final long CLOSED             = 304L;

  public static final long AWAIT_START        = 400L;
  public static final long SYNC_ARENA         = 405L;
  public static final long POKE_RUMBLE        = 406L;

  public static final long RESTART            = 500L;
  public static final long START              = 501L;
  public static final long STOP               = 502L;
  public static final long POKE_MOVE          = 503L;
  public static final long POKE_WAIT          = 504L;
  public static final long PLAY_MOVE          = 505L;
  public static final long REPLAY             = 506L;

  // request types
  public static final long PLAYGAME_REQ       = 600L;
  public static final long JOINGAME_REQ       = 601L;

  public static final long QUIT_GAME          = 700L;

  // session status
  public static final long S_NOT_CONNECTED    = 0L;
  public static final long S_CONNECTED        = 1L;


}

