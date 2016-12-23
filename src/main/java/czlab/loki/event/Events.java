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

  // Reply codes
  public static final long OK         = 200L;
  public static final long ERROR      = 500L;

  // request codes
  public static final long PLAYGAME_REQ       = 600L;
  public static final long PLAYREQ_OK         = 601L;
  public static final long PLAYREQ_NOK        = 602L;

  public static final long JOINGAME_REQ       = 651L;
  public static final long JOINREQ_OK         = 652L;
  public static final long JOINREQ_NOK        = 653L;

  // user and room codes
  public static final long USER_NOK           = 700L;
  public static final long GAME_NOK           = 701L;
  public static final long ROOM_NOK           = 702L;
  public static final long ROOM_FILLED        = 703L;
  public static final long ROOMS_FULL         = 704L;

  // msg codes
  public static final long PLAYER_JOINED      = 800L;
  public static final long AWAIT_START        = 801L;

  public static final long CONNECTED          = 820L;
  public static final long STARTED            = 821L;
  public static final long CLOSED             = 822L;

  public static final long RESTART            = 840L;
  public static final long START              = 841L;
  public static final long STOP               = 842L;

  public static final long POKE_RUMBLE        = 860L;
  public static final long POKE_MOVE          = 861L;
  public static final long POKE_WAIT          = 862L;
  public static final long SYNC_ARENA         = 863L;

  public static final long REPLAY             = 880L;
  public static final long PLAY_MOVE          = 881L;

  // end game
  public static final long QUIT          = 911L;

  // session status
  public static final long S_NOT_CONNECTED    = 0L;
  public static final long S_CONNECTED        = 1L;


}

