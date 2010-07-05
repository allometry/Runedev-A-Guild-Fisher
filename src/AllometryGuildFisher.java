import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.net.URL;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Map;

import javax.imageio.ImageIO;

import org.rsbot.bot.input.CanvasWrapper;
import org.rsbot.event.events.ServerMessageEvent;
import org.rsbot.event.listeners.PaintListener;
import org.rsbot.event.listeners.ServerMessageListener;
import org.rsbot.script.Calculations;
import org.rsbot.script.Script;
import org.rsbot.script.ScriptManifest;
import org.rsbot.script.wrappers.RSArea;
import org.rsbot.script.wrappers.RSNPC;
import org.rsbot.script.wrappers.RSObject;
import org.rsbot.script.wrappers.RSTile;

@ScriptManifest(authors = { "Allometry" }, category = "Fishing", name = "Allometry Guild Fisher", version = 0.1, description = "" +
		"<html>" +
		"<head>" +
		"</head>" +
		"<body>" +
		"<div style=\"text-align: center;\">" +
		"<label for=\"fishType\">I'd like to fish:</label>" +
		"<select name=\"fishType\" id=\"fishType\">" +
		"<option value=\"312\">Lobster</option>" +
		"<option value=\"312\">Swordfish</option>" +
		"<option value=\"313\">Shark</option>" +
		"</select>" +
		"</div>" +
		"</body>" +
		"</html>")

public class AllometryGuildFisher extends Script implements PaintListener, ServerMessageListener {
	private int npcShark = 313, npcLobsterSwordfish = 312, npcFish = 0;
	private int lobsterPot = 301, regularHarpoon = 311, sacredClayHarpoon = 311, barbTailHarpoon = 10129;
	private int fishCaught = 0, objBankBooth = 49018;
	
	private long startTime;
	
	private RSArea fishingGuildNorthDock = new RSArea(new RSTile(2598, 3419), new RSTile(2605, 3426));
	private RSArea fishingGuildBankArea = new RSArea(new RSTile(2585, 3420), new RSTile(2587, 3424));
	private RSTile[] northDockTiles = { new RSTile(2586, 3422), new RSTile(2591, 3420), new RSTile(2596, 3420), new RSTile(2599, 3422) };
	private RSNPC currentFishingNPC = null;
	
	private FishingHole fishingHole = null;
	private Thread fishingHoleMonitor = null;

	private BufferedImage basketImage;
	private BufferedImage clockImage;

	private String actionAtNPC;
	
	private boolean isFinishing = false, isMonitoringFishingHoles = true;
	
	private void synchronizeThreads() {
		try {
		if(isPaused || !isLoggedIn() || isWelcomeScreen() || isLoginScreen() || isFinishing) {
			if(fishingHoleMonitor != null && fishingHole != null) {
				if(fishingHoleMonitor.isAlive()) {
					isMonitoringFishingHoles = false;
				} else {
					fishingHole = null;
					fishingHoleMonitor = null;
				}
			}
		} else {
			if(fishingHoleMonitor == null && fishingHole == null) {
				fishingHole = new FishingHole();
				fishingHoleMonitor = new Thread(fishingHole);
				fishingHoleMonitor.start();
			}
		}
		} catch(Exception e) {
			log("Warning: Unable to Synchronize Threads.");
		}
	}
	
	private class FishingHole implements Runnable {
		@Override
		public void run() {
			while(isMonitoringFishingHoles) {
				try {
					if(currentFishingNPC == null) {
						RSNPC possibleFishingNPC = getNPCInArea(fishingGuildNorthDock, npcFish);
						log("Notice: Looking for fishing spot...");
						if(possibleFishingNPC != null) {
							currentFishingNPC = possibleFishingNPC;
							log("Notice: Found new fishing spot...");
						} else {
							log("Notice: Haven't found a new fishing spot yet...");
						}
					}
					
					if(currentFishingNPC != null) {
						if(getNPCAt(currentFishingNPC.getLocation()) != null) {
							if(getNPCAt(currentFishingNPC.getLocation()).getID() != npcFish) {
								currentFishingNPC = null;
								log("Notice: Fishing spot changed...");
							}
						}
					}
				} catch(Exception e) {
					log("Notice: FishingHole Thread Caught an Exception.");
				}
			}
			return ;
		}
	}
	
	private RSNPC getNPCAt(RSTile atTile) {
		try {
			if(!tileOnScreen(atTile)) return null;
			
			RSNPC[] npcs = getNPCArray(false);
			for (RSNPC npc : npcs) {
				if(npc.getLocation().equals(atTile))
					return npc;
			}
		} catch(Exception e) {
			log("Notice: Unable to Find an NPC at the Tile.");
		}
		
		return null;
	}
	
	private RSNPC getNPCInArea(RSArea inArea, int npcID) {
		int distanceClosestToPlayer = 9999;
		RSNPC npcClosestToPlayer = null;
		
		try {
			for(RSNPC npc : getNPCArray(false)) {
				if(npc.getID() == npcID) {
					int distanceToPlayer = distanceTo(npc.getLocation());
					if(distanceToPlayer < distanceClosestToPlayer && npc.getLocation().getY() >= 3419) {
						distanceClosestToPlayer = distanceToPlayer;
						npcClosestToPlayer = npc;
					}
				}
			}
		} catch(Exception e) {
			log("Notice: Unable to Find an NPC in the Area.");
		}
		
		return npcClosestToPlayer;
	}
	
	@Override
	public boolean onStart(final Map<String, String> args) {
		try {
			basketImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/basket.png"));
			clockImage = ImageIO.read(new URL("http://scripts.allometry.com/icons/clock.png"));
		} catch (Exception ignoredException) {}
			
		final String fishType = args.get("fishType");
		
		if(fishType.contains("Lobster")) {
			npcFish = npcLobsterSwordfish;
			actionAtNPC = "Cage";
		}
		
		if(fishType.contains("Swordfish")) {
			npcFish = npcLobsterSwordfish;
			actionAtNPC = "Harpoon";
		}
		
		if(fishType.contains("Shark")) {
			npcFish = npcShark;
			actionAtNPC = "Harpoon";
		}
		
		if(npcFish == 0 && actionAtNPC == "") {
			log("Invalid arguments, exiting...");
			return false;
		}
		
		fishingHole = new FishingHole();
		fishingHoleMonitor = new Thread(fishingHole);
		fishingHoleMonitor.start();
		
		startTime = System.currentTimeMillis();
		
		return true;
	}

	@Override
	public int loop() {
		try {
			setCameraAltitude(true);
			
			if((isIdle() && !isInventoryFull()) && fishingGuildNorthDock.contains(getLocation()) && currentFishingNPC != null) {
				if(getNPCAt(currentFishingNPC.getLocation()).getID() == npcFish) {
					setCameraRotation(getAngleToTile(currentFishingNPC.getLocation()) * 2);
					
					if(!tileOnScreen(currentFishingNPC.getLocation()))
						walkTo(currentFishingNPC.getLocation());
					
					atNPC(currentFishingNPC, actionAtNPC, true);
					return 3000;
				}
			}
			
			if(isInventoryFull() && !fishingGuildBankArea.contains(getLocation())) {
				walkPathMM(randomizePath(reversePath(northDockTiles), 2, 2));
				return 1500;
			}
			
			if(!isInventoryFull() && !fishingGuildNorthDock.contains(getLocation())) {
				walkPathMM(randomizePath(northDockTiles, 2, 2));
				return 1500;
			}
			
			if((isInventoryFull() && fishingGuildBankArea.contains(getLocation())) || getNearestObjectByID(objBankBooth) != null) {
				if(!bank.isOpen()) {
					RSObject booth = getNearestObjectByID(objBankBooth);
					
					if(booth != null) {
						atObject(booth, "Quickly");
						return 1500;
					}
				} else {
					bank.depositAllExcept(lobsterPot, regularHarpoon, sacredClayHarpoon, barbTailHarpoon);
					bank.close();
					return 2000;
				}
			}
		} catch(Exception e) {
			log("Notice: Loop Caught an Exception.");
			for(StackTraceElement ste : e.getStackTrace()) {
				log(ste.toString());
			}
		}
		
		return 1;
	}
	
	@Override
	public void onFinish() {
		isFinishing = true;
		synchronizeThreads();
		
		return ;
	}

	@Override
	public void onRepaint(Graphics guiCanvas) {
		//Synchronize Threads on Tick
		synchronizeThreads();
		
		if(isPaused || !isLoggedIn() || isWelcomeScreen() || isLoginScreen()) return ;
		
		//Setup Graphics2D Canvas
		Graphics2D g = (Graphics2D)guiCanvas;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		int width = CanvasWrapper.getGameWidth() - 362;
		
		//HUD: Draw Layout
		RoundRectangle2D clockBackground = new RoundRectangle2D.Float(
				width,
				20,
				89,
				26,
				5,
				5);
		
		RoundRectangle2D scoreboardBackground = new RoundRectangle2D.Float(
				20,
				20,
				89,
				26,
				5,
				5);
		
		g.setColor(new Color(0, 0, 0, 127));
		g.fill(clockBackground);
		g.fill(scoreboardBackground);
		
		//HUD: Draw Images
		ImageObserver observer = null;
		g.drawImage(basketImage, 25, 25, observer);
		g.drawImage(clockImage, width + 68, 25, observer);
		
		//HUD: Draw Text
		g.setColor(Color.white);
		g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		
		g.drawString(nf.format(fishCaught), 48, 39);
		
		if(startTime == 0)
			g.drawString("Loading", width + 5, 37);
		else
			g.drawString(millisToClock(System.currentTimeMillis() - startTime), width + 5, 37);
		
		//Canvas: Paint Current Tile
		try {
			if(fishingHole != null && fishingHoleMonitor != null)
				if(fishingHoleMonitor.isAlive())
					paintTile(g, currentFishingNPC.getLocation(), Color.cyan, new Color(0, 0, 255, 75));
		} catch(Exception e) {
			log("Notice: Unable to Paint Current Tile.");
		}
		
		return ;
	}
	
	private void paintTile(final Graphics g, final RSTile t, final Color outline, final Color fill) {
		final Point pn = Calculations.tileToScreen(t.getX(), t.getY(), 0, 0, 0);
		final Point px = Calculations.tileToScreen(t.getX(), t.getY(), 1, 0, 0);
		final Point py = Calculations.tileToScreen(t.getX(), t.getY(), 0, 1, 0);
		final Point pxy = Calculations
		.tileToScreen(t.getX(), t.getY(), 1, 1, 0);
		if (py.x == -1 || pxy.x == -1 || px.x == -1 || pn.x == -1) {
		return;
		}
		g.setColor(outline);
		g.drawPolygon(new int[]{py.x, pxy.x, px.x, pn.x}, new int[]{py.y,
		pxy.y, px.y, pn.y}, 4);
		g.setColor(fill);
		g.fillPolygon(new int[]{py.x, pxy.x, px.x, pn.x}, new int[]{py.y,
		pxy.y, px.y, pn.y}, 4);
	}

	@Override
	public void serverMessageRecieved(ServerMessageEvent serverMessage) {
		String message = serverMessage.getMessage();
		
		if (message.contains("shark"))
			fishCaught++;
		if (message.contains("swordfish"))
			fishCaught++;
		if (message.contains("lobster"))
			fishCaught++;
		if (message.contains("tuna"))
			fishCaught++;
		
		return;
	}
	
	private String millisToClock(long milliseconds) {
		long seconds = (milliseconds / 1000), minutes = 0, hours = 0;
		
		if (seconds >= 60) {
			minutes = (seconds / 60);
			seconds -= (minutes * 60);
		}
		
		if (minutes >= 60) {
			hours = (minutes / 60);
			minutes -= (hours * 60);
		}
		
		return (hours < 10 ? "0" + hours + ":" : hours + ":")
				+ (minutes < 10 ? "0" + minutes + ":" : minutes + ":")
				+ (seconds < 10 ? "0" + seconds : seconds);
	}
}
