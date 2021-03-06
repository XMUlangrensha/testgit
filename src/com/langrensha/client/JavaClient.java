package com.langrensha.client;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.langrensha.model.Player;
import com.langrensha.model.Role;
import com.langrensha.model.Witch;
import com.langrensha.utility.Config;
import com.langrensha.utility.Message;
import com.langrensha.utility.MyTimer;

public class JavaClient implements Runnable {
	private static final long serialVersionUID = -1247962671919889272L;
	private String serverIP = "127.0.0.1";
	private Player myPlayer;
	private boolean myTurn = false;
	private Gson gson = new GsonBuilder()
			.excludeFieldsWithoutExposeAnnotation().create();
	int realRoleCount[];
	MyTimer timer;
	// for test
	private JTextArea textArea;
	JButton submitBtn;

	public int getRoleId() {
		if (myPlayer != null)
			return myPlayer.getRole().getId();
		return 0;
	}

	public JavaClient(JTextArea textArea, JButton submitBtn) {
		this.textArea = textArea;
		this.submitBtn = submitBtn;
	}

	private void displayMessage(final String messageToDisplay) {
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				textArea.append(messageToDisplay + "\n");
			}
		});
	}

	public void start(String serverIP, String myName, boolean isOwner,
			String other) {
		myPlayer = new Player();
		myPlayer.setName(myName);
		myPlayer.setOwner(isOwner);
		try {
			// 连接服务器的socket，默认超时时间无线大
			Socket connection = new Socket(InetAddress.getByName(serverIP),
					12345);
			myPlayer.setSocket(connection);
		} catch (IOException ioException) {
			ioException.printStackTrace();
		}
		// 线程池
		ExecutorService worker = Executors.newFixedThreadPool(1);
		worker.execute(this);
		// 发送用户信息+房间人数or房号
		myPlayer.output(new Message(Event.CLIENT_INFO, myPlayer, other));
	}

	@Override
	public void run() {
		int count = 0;
		while (true) {
			String msg = myPlayer.input();
			if (msg == "") {
				count++;
				System.out.println("与服务器连接断开！尝试重新连接第" + count + "次");
				if (count >= 5) {
					System.out.println("重新连接失败！请联系管理员！");
					break;
				}
			} else {
				System.out.println("JavaClient收到：" + msg);
				processMessage(msg);
				count = 0;
			}
		}
	}

	private void processMessage(String message) {
		JsonParser parser = new JsonParser();
		JsonElement element = parser.parse(message);
		if (element.isJsonArray()) {
			JsonArray array = parser.parse(message).getAsJsonArray();
			byte event = gson.fromJson(array.get(0), Byte.class);
			switch (event) {
			case Event.SERVER_ROOM:
				int myRoom = gson.fromJson(array.get(1), Integer.class);
				int myId = gson.fromJson(array.get(2), Integer.class);
				myPlayer.setId(myId);
				timer = new MyTimer(Config.TIMER_OUT, myId);
				displayMessage("我是玩家" + myId + ",加入房间" + myRoom);

				// for test
				submitBtn.setText("玩家" + myId);

				break;
			case Event.SERVER_JOINED_COUNT:
				int realCount = gson.fromJson(array.get(1), Integer.class);
				int initCount = gson.fromJson(array.get(2), Integer.class);
				displayMessage("等待其他玩家加入(" + realCount + "/" + initCount
						+ ")……");
				break;
			case Event.SERVER_ALL_ROLE_INFO:
				displayMessage("角色分配");
				realRoleCount = gson.fromJson(array.get(1), int[].class);
				String text = "";
				for (int i = 0; i < realRoleCount.length; i++)
					if (realRoleCount[i] != 0)
						text += Role.toRoleName(i) + ":" + realRoleCount[i]
								+ "人\n";
				displayMessage(text);

				break;
			case Event.SERVER_ALL_PLAYER_INFO:
				displayMessage("所有玩家信息");
				String[] roleNames = gson
						.fromJson(array.get(1), String[].class);
				text = "";
				for (int i = 1; i < roleNames.length; i++) {
					text += "玩家" + i + ":" + roleNames[i] + "\n";
				}
				displayMessage(text);

				displayMessage("狼人杀游戏开始");
				break;
			case Event.SERVER_ROLE_INFO:
				Role role = gson.fromJson(array.get(1), Role.class);
				myPlayer.setRole(role);
				displayMessage("你的身份是：" + role.getName());
				break;
			case Event.SERVER_ELECTION_START:
				// 选警长
				displayMessage("【是否参选警长？】");
				// 倒计时
				timer.execute();
				myTurn = true;
				break;
			case Event.SERVER_ELECTION_OVER:
				displayMessage("警长候选人：");
				int candidateId = gson.fromJson(array.get(1), int.class);
				displayMessage("玩家" + candidateId);
				break;
			case Event.SERVER_SPEECH_START:
				int speechId = gson.fromJson(array.get(1), int.class);
				displayMessage("玩家" + speechId + "发言");
				// 如果轮到自己发言，则计时
				if (myPlayer.getId() == speechId) {
					displayMessage("【请发言！】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_VOTE_START:
				displayMessage("【开始投票】");
				boolean[] toVoteIds = gson.fromJson(array.get(1),
						boolean[].class);
				text = "候选人有：";
				for (int id = 1; id < toVoteIds.length; id++)
					if (toVoteIds[id])
						text += "玩家" + id + " ";
				displayMessage(text);
				if (myPlayer.getRole().getStatus() == Role.ALIVE) {
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_VOTE_OVER:
				displayMessage("投票结果：");
				boolean[][] voteInfos = gson.fromJson(array.get(1),
						boolean[][].class);
				for (int toVoteId = 1; toVoteId < voteInfos[0].length; toVoteId++) {
					if (voteInfos[0][toVoteId]) {
						String item = "玩家" + toVoteId + "——";
						for (int voteId = 1; voteId < voteInfos[toVoteId].length; voteId++) {
							if (voteInfos[voteId][toVoteId])
								item += voteId + " ";
						}
						displayMessage(item);
					}
				}
				break;
			case Event.SERVER_SHERRIF_ID:
				int id = gson.fromJson(array.get(1), Integer.class);
				if (id == 0)
					displayMessage("没有警长");
				else {
					displayMessage("警长是玩家" + id);
					if (myPlayer.getId() == id)
						myPlayer.getRole().setSheriff(true);
				}
				break;

			case Event.SERVER_THIEF_START:
				displayMessage("盗贼请睁眼，盗贼请换牌！");
				if (myPlayer.getRole().getId() == Role.THIEF) {
					int[] waitingRoles = gson.fromJson(array.get(1),
							int[].class);
					text = "【" + Role.toRoleName(waitingRoles[0]) + " 或者 "
							+ Role.toRoleName(waitingRoles[1]) + "】";
					displayMessage(text);
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_THIEF_OVER:
				if (myPlayer.getRole().getId() == Role.THIEF) {
					Role newRole = gson.fromJson(array.get(1), Role.class);
					myPlayer.setRole(newRole);
					displayMessage("你的新身份是：" + newRole.getName());
				}
				displayMessage("盗贼请闭眼");
				break;
			case Event.SERVER_CUPID_START:
				displayMessage("丘比特请睁眼，丘比特请连情侣！");
				if (myPlayer.getRole().getId() == Role.CUPID) {
					displayMessage("【请选择任意两位玩家！】");
					timer.execute();
					myTurn = true;
				}
				break;

			case Event.SERVER_CUPID_OVER:
				displayMessage("丘比特请闭眼");
				int firstId = gson.fromJson(array.get(1), int.class);
				int secondId = gson.fromJson(array.get(2), int.class);
				if (myPlayer.getRole().getId() == Role.CUPID) {
					displayMessage("已连接情侣 玩家" + firstId + "和 玩家" + secondId);
				}
				if (myPlayer.getId() == firstId || myPlayer.getId() == secondId)
					displayMessage("玩家" + firstId + " 和 玩家" + secondId
							+ "成为 情侣");
				break;

			case Event.SERVER_SEER_START:
				displayMessage("预言家请睁眼，预言家请验人！");
				if (myPlayer.getRole().getId() == Role.SEER
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					displayMessage("【请选择任意一位玩家！】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_SEER_OVER:
				if (myPlayer.getRole().getId() == Role.SEER
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					int toSeeId = gson.fromJson(array.get(1), int.class);
					int roleId = gson.fromJson(array.get(2), int.class);
					displayMessage("玩家" + toSeeId + "的角色是"
							+ Role.toRoleName(roleId));
				}
				displayMessage("预言家请闭眼");
				break;
			case Event.SERVER_WOLF_START:
				displayMessage("狼人请睁眼，狼人请杀人！");
				if (myPlayer.getRole().getId() == Role.WOLF
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					displayMessage("【请选择任意一位玩家！】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_WOLF_ATTEMP_START:
				if (myPlayer.getRole().getId() == Role.WOLF) {
					int killerId = gson.fromJson(array.get(1), int.class);
					int toKilledId = gson.fromJson(array.get(2), int.class);
					displayMessage("玩家" + killerId + " 杀 玩家" + toKilledId);
				}
				break;
			case Event.SERVER_WOLF_ATTEMP_OVER:
				if (myPlayer.getRole().getId() == Role.WOLF) {
					int toKillId = gson.fromJson(array.get(1), int.class);
					if (toKillId == 0) {
						displayMessage("狼人 必须杀一人");
						displayMessage("【请选择任意一位玩家！】");
						timer.execute();
						myTurn = true;
					} else if (toKillId == -1) {
						displayMessage("狼人， 请统一意见！");
						displayMessage("【请选择任意一位玩家！】");
						timer.execute();
						myTurn = true;
					} else
						displayMessage("狼人 杀 玩家" + toKillId);
				}
				break;
			case Event.SERVER_WOLF_OVER:
				displayMessage("狼人请闭眼");
				break;
			case Event.SERVER_WITCHSAVE_START:
				displayMessage("女巫请睁眼，女巫请救人或毒人！");
				if (myPlayer.getRole().getId() == Role.WITCH
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					int toKillId = gson.fromJson(array.get(1), int.class);
					displayMessage("【今晚被杀的人是玩家" + toKillId + "，请问你救是或不救？】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_WITCHPOISON_START:
				if (myPlayer.getRole().getId() == Role.WITCH
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					displayMessage("【毒是不毒？】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_WITCHSAVE_OVER:
				if (myPlayer.getRole().getId() == Role.WITCH
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					int playerId = gson.fromJson(array.get(1), int.class);
					if (playerId == 0) {
						displayMessage("女巫救人失败，解药已用完");
					} else {
						displayMessage("女巫救人成功，玩家" + playerId + "被解救");
					}
				}
				break;
			case Event.SERVER_WITCHPOISON_OVER:
				if (myPlayer.getRole().getId() == Role.WITCH
						&& myPlayer.getRole().getStatus() == Role.ALIVE) {
					int playerId = gson.fromJson(array.get(1), int.class);
					if (playerId == 0) {
						displayMessage("女巫毒人失败，毒药已用完");
					} else {
						displayMessage("女巫毒人成功，玩家" + playerId + "被毒死");
					}
				}
				displayMessage("女巫请闭眼！");
				break;
			case Event.SERVER_NIGHT_DEATH:
				int[] deads = gson.fromJson(array.get(1), int[].class);
				int dead_count = 0;
				text = "昨晚死亡的人有：\n";
				for (int i = 1; i < deads.length; i++) {
					if (deads[i] == Role.KILLED) {
						text += "玩家" + i + " 被狼人杀死\n";
						dead_count++;
					} else if (deads[i] == Role.POISONED) {
						text += "玩家" + i + " 被女巫毒死\n";
						dead_count++;
					} else if (deads[i] == Role.SUICIDED) {
						text += "玩家" + i + " 殉情而死\n";
						dead_count++;
					}
				}
				if (dead_count == 0)
					displayMessage("昨晚是一个平安夜");
				else
					displayMessage(text);

				break;
			case Event.SERVER_HUNTER_START:
				int status = gson.fromJson(array.get(1), int.class);
				if (status == Role.EXECUTED)
					displayMessage("猎人被处死，猎人报复！");
				else if (status == Role.KILLED)
					displayMessage("猎人被狼人杀死，猎人报复！");
				if (myPlayer.getRole().getId() == Role.HUNTER) {
					displayMessage("【请指定要猎杀的一名玩家】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_HUNTER_OVER:
				int[] huntIds = gson.fromJson(array.get(1), int[].class);
				displayMessage("猎人报复，猎杀了玩家" + huntIds[0]);
				if (huntIds.length > 1) {
					displayMessage("玩家" + huntIds[1] + "殉情");
				}
				break;
			case Event.SERVER_SHERIFF_START:
				displayMessage("警长死亡，警长指定新的继承人！");
				if (myPlayer.getRole().isSheriff()) {
					myPlayer.getRole().setSheriff(false);
					displayMessage("【请指定一名玩家作为新警长】");
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_SHERIFF_OVER:

				int newId = gson.fromJson(array.get(1), int.class);
				displayMessage("新警长是 玩家" + newId);
				if (myPlayer.getId() == newId) {
					myPlayer.getRole().setSheriff(true);
					displayMessage("【你是新警长！】");
				}
				break;
			case Event.SERVER_VILLAGER_START:
				displayMessage("村民们不会坐以待毙，找到这些狼人并处决他们！");
				if (myPlayer.getRole().getStatus() == Role.ALIVE) {
					timer.execute();
					myTurn = true;
				}
				break;
			case Event.SERVER_EXECUTE_ID:
				int[] executeIds = gson.fromJson(array.get(1), int[].class);
				if (executeIds[0] == 0)
					displayMessage("平票已达三次，没有处死任何人");
				else {
					displayMessage("村民们投票，处死了玩家" + executeIds[0]);
					// amIDead(executeIds[0]);
					if (executeIds.length > 1) {
						displayMessage("玩家" + executeIds[1] + "殉情");
						// amIDead(executeIds[1]);
					}
				}
				break;
			case Event.SERVER_OPEN_EYES:
				int today = gson.fromJson(array.get(1), int.class);
				displayMessage("天亮请睁眼，今天是第" + today + "天");
				break;
			case Event.SERVER_CLOSE_EYES:
				int day = gson.fromJson(array.get(1), Integer.class);
				displayMessage("天黑请闭眼，今天是第" + day + "天");
				break;
			case Event.SERVER_YOU_ARE_DEAD:
				status = gson.fromJson(array.get(1), Integer.class);
				myPlayer.getRole().setStatus(status);
				displayMessage("【--------你牺牲了！--------】");
				break;
			case Event.SERVER_GAME_RESULT:
				int result = gson.fromJson(array.get(1), Integer.class);
				switch (result) {
				case Event.WOLF_WIN:
					displayMessage("【狼人阵营胜利】");
					break;
				case Event.VILLAGER_WIN:
					displayMessage("【村民阵营胜利】");
					break;
				case Event.LOVER_WIN:
					displayMessage("【情侣阵营胜利】");
					break;
				case Event.EQUAL:
					displayMessage("【死光光，平局】");
					break;
				default:
					break;
				}
				break;
			default:
				displayMessage(array.toString());
				break;
			}
		} else {
			displayMessage(element + "\n");
		}

	}

	// ////////////////////////////////////////////////////////////////////////////////////////////////////////////
	private boolean send(Message msg) {
		if (myTurn) {
			myPlayer.output(msg);
			if (timer != null)
				timer.cancel();
			myTurn = false;
			return true;
		} else {
			return false;
		}
	}

	public boolean sendElectionAction(boolean isJoin) {
		return send(new Message(Event.CLIENT_ELECTION_ACTION, isJoin));
	}

	public boolean sendSpeechOver() {
		return send(new Message(Event.CLIENT_SPEECH_OVER));
	}

	public boolean sendVoteAction(int toVoteId) {
		return send(new Message(Event.CLIENT_VOTE_ACTION, toVoteId));
	}

	public boolean sendThiefAction(int roleIndex) {
		return send(new Message(Event.CLIENT_THIEF_ACTION, roleIndex));
	}

	public boolean sendCupidAction(int firstId, int secondId) {
		return send(new Message(Event.CLIENT_CUPID_ACTION, firstId, secondId));
	}

	public boolean sendSeerAction(int roleIndex) {
		return send(new Message(Event.CLIENT_SEER_ACTION, roleIndex));
	}

	public boolean sendWolfAction(int killId) {
		return send(new Message(Event.CLIENT_WOLF_ACTION, killId));
	}

	public boolean sendWitchSaveAction(int playerId) {
		return send(new Message(Event.CLIENT_WITCHSAVE_ACTION, playerId));
	}

	public boolean sendWitchPoisonAction(int playerId) {
		return send(new Message(Event.CLIENT_WITCHPOISON_ACTION, playerId));
	}

	public boolean sendSheriffAction(int newSheriffId) {
		return send(new Message(Event.CLIENT_SHERIFF_ACTION, newSheriffId));
	}

	public boolean sendHuntAction(int toHuntId) {
		return send(new Message(Event.CLIENT_HUNTER_ACTION, toHuntId));
	}

}
