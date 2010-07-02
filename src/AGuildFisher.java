import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
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
import org.rsbot.script.Script;
import org.rsbot.script.ScriptManifest;
import org.rsbot.script.wrappers.RSArea;
import org.rsbot.script.wrappers.RSNPC;
import org.rsbot.script.wrappers.RSObject;
import org.rsbot.script.wrappers.RSTile;

@ScriptManifest(authors = { "Allometry" }, category = "Fishing", name = "A. Guild Fisher", version = 0.1, description = "" +
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
public class AGuildFisher extends Script implements PaintListener, ServerMessageListener {	
	private int npcShark = 313, npcLobsterSwordfish = 312, npcFish = 0, fishCaught = 0;
	private int lobsterPot = 301, regularHarpoon = 311, sacredClayHarpoon = 311, barbTailHarpoon = 10129;
	private long startTime = 0;
	
	private String actionAtNPC = "";
	
	private RSArea northDockArea = new RSArea(new RSTile(2598, 3419), new RSTile(2605, 3426));
	private RSArea guildBankArea = new RSArea(new RSTile(2585, 3420), new RSTile(2587, 3424));
	private RSTile[] northDockTiles = { new RSTile(2586, 3422), new RSTile(2591, 3420), new RSTile(2596, 3420), new RSTile(2599, 3422) };
	
	private RSNPC currentFishingNPC = null;
	private RSTile currentFishingTile = null;
	private boolean changedFishingTile = false;
	
	private BufferedImage basketImage;
	private BufferedImage clockImage;
	

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
			
				
		startTime = System.currentTimeMillis();
		
		return true;
	}
	
	private RSNPC getNPCAt(RSTile atTile) {
		if(!tileOnScreen(atTile)) return null;
		
		RSNPC[] npcs = getNPCArray(false);
		for (RSNPC rsnpc : npcs) {
			if(rsnpc.getLocation().equals(atTile))
				return rsnpc;
		}
		
		return null;
	}
	
	private RSNPC getNPCInArea(RSArea inArea, int npcID) {
		try {
			RSNPC[] npcs = getNPCArray(false);
			for (RSNPC rsnpc : npcs) {
				if(inArea.contains(rsnpc.getLocation()) && rsnpc.getID() == npcID)
					return rsnpc;
			}
		} catch(Exception e) {}
		
		return null;
	}
	
	private void fishingHoleMonitor() {
		long failsafeTime = 0;
		
		if(northDockArea.contains(getLocation())) {
			if(currentFishingNPC == null) {
				findFishingHole();
				log("Current Fishing NPC is NULL");
			}
			
			if(getNPCAt(currentFishingTile) != null) {
				if(getNPCAt(currentFishingTile).getID() != npcFish) {
					findFishingHole();
					log("Object doesn't match required ID, finding new spot");	
				}
			} else {
				findFishingHole();
				log("Fishing spot moved, finding new spot");
			}
			
			if(isIdle()) {
				if(failsafeTime == 0) failsafeTime = System.currentTimeMillis() + 10000;
				
				if(System.currentTimeMillis() > failsafeTime) {
					findFishingHole();
					log("Failsafe activated, finding new spot");
				}
			} else {
				failsafeTime = 0;
			}
		}
		
		return ;
	}
	
	private void findFishingHole() {
		boolean areaFound = false;
		
		try {
			while(!areaFound) {
				currentFishingNPC = getNearestNPCByID(npcFish);
				if(northDockArea.contains(currentFishingNPC.getLocation())) areaFound = true;
			}
			
			currentFishingTile = currentFishingNPC.getLocation();
			changedFishingTile = true;
		} catch(Exception e) {}
	}
	
	@Override
	public int loop() {
		if(!isLoggedIn()) return 1;
		
		if(northDockArea.contains(getLocation())) {
			fishingHoleMonitor();
			
			if(getNPCInArea(northDockArea, npcFish) == null) {
				walkTileMM(northDockArea.getRandomTile());
				
				return 1500;
			}
		}
		
		if((isIdle() && !isInventoryFull()) && northDockArea.contains(getLocation()) || changedFishingTile) {
			setCameraRotation(getAngleToTile(currentFishingTile) * 2);
			walkTo(currentFishingTile);
			atNPC(currentFishingNPC, actionAtNPC, true);
			changedFishingTile = false;
			return 3000;
		}
		
		if(isInventoryFull() && !guildBankArea.contains(getLocation())) {
			walkPathMM(randomizePath(reversePath(northDockTiles), 2, 2));
			return 1500;
		}
		
		if(!isInventoryFull() && !northDockArea.contains(getLocation())) {
			walkPathMM(randomizePath(northDockTiles, 2, 2));
			return 1500;
		}
		
		if(isInventoryFull() && guildBankArea.contains(getLocation())) {
			if(!bank.isOpen()) {
				RSObject booth = getNearestObjectByID(49018);
				
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
		
		return 1;
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
	
	@Override
	public void onFinish() {
		return ;
	}

	@Override
	public void onRepaint(Graphics g2) {
		if(isPaused || isLoginScreen() || isWelcomeScreen()) return ;
		
		Graphics2D g = (Graphics2D)g2;
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		
		int width = CanvasWrapper.getGameWidth() - 362;
		
		//Rectangles
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
		
		//Text
		g.setColor(Color.white);
		g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		
		NumberFormat nf = NumberFormat.getIntegerInstance(Locale.US);
		
		g.drawString(nf.format(fishCaught), 48, 39);
		
		if(startTime == 0)
			g.drawString("Loading", width + 5, 37);
		else
			g.drawString(millisToClock(System.currentTimeMillis() - startTime), width + 5, 37);
		
		//Images
		ImageObserver observer = null;
		g.drawImage(basketImage, 25, 25, observer);
		g.drawImage(clockImage, width + 68, 25, observer);
		
		return ;
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