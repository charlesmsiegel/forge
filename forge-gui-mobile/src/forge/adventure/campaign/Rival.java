package forge.adventure.campaign;

import com.google.common.collect.Lists;
import forge.adventure.util.SaveFileContent;
import forge.adventure.data.EnemyData;
import forge.adventure.util.SaveFileData;
import forge.deck.CardPool;
import forge.item.PaperCard;

import java.util.List;

/**
 * A formerly generic opponent that won a card from the player and therefore became a persistent
 * NPC (MVP.md §16). Keeps the exact printings it took so they can be reclaimed (§17).
 */
public class Rival implements SaveFileContent {
    public String id = "";
    public String name = "";
    /** Name of the enemy template (world/enemies.json) this rival was promoted from. */
    public String baseEnemyName = "";
    public int playerWins;
    public int playerLosses;
    public long createdAt;
    private final CardPool cardsTakenFromPlayer = new CardPool();
    private final CardIdentityLedger takenCopies = new CardIdentityLedger();

    public List<OwnedCard> getTakenCopies() {
        takenCopies.reconcile(cardsTakenFromPlayer);
        return takenCopies.copies();
    }

    public void addTaken(OwnedCard copy) {
        takenCopies.add(copy);
        cardsTakenFromPlayer.add(copy.card);
    }

    public boolean removeTaken(OwnedCard copy) {
        if (!takenCopies.remove(copy)) return false;
        cardsTakenFromPlayer.remove(copy.card);
        return true;
    }

    public Rival() {
    }

    public Rival(String id, String name, String baseEnemyName) {
        this.id = id;
        this.name = name;
        this.baseEnemyName = baseEnemyName;
        this.createdAt = System.currentTimeMillis();
    }

    /** Exact printings this rival personally won from the player, one entry per copy. */
    public List<PaperCard> getCardsTakenFromPlayer() {
        return cardsTakenFromPlayer.toFlatList();
    }

    public CardPool getTakenPool() {
        return cardsTakenFromPlayer;
    }

    public void addTaken(PaperCard card) {
        addTaken(OwnedCard.mint(card));
    }

    /** @return true if one copy was removed (the player reclaimed it). */
    public boolean removeTaken(PaperCard card) {
        for (OwnedCard copy : getTakenCopies())
            if (copy.card.equals(card)) return removeTaken(copy);
        return false;
    }

    public boolean hasTaken(PaperCard card) {
        return cardsTakenFromPlayer.count(card) > 0;
    }

    public String record() {
        return playerWins + "-" + playerLosses;
    }

    /** A camp rival keeps its deck and life, but is never a one-time boss encounter. */
    public EnemyData encounterData(EnemyData template) {
        EnemyData encounter = new EnemyData(template);
        encounter.nameOverride = name;
        encounter.boss = false;
        encounter.nextEnemy = null;
        return encounter;
    }

    @Override
    public void load(SaveFileData data) {
        id = data.readString("id");
        name = data.readString("name");
        baseEnemyName = data.readString("baseEnemyName");
        playerWins = data.readInt("playerWins");
        playerLosses = data.readInt("playerLosses");
        createdAt = data.containsKey("createdAt") ? data.readLong("createdAt") : 0;
        cardsTakenFromPlayer.clear();
        takenCopies.clear();
        if (data.containsKey("cardsTaken")) {
            String[] lines = (String[]) data.readObject("cardsTaken");
            if (lines != null)
                cardsTakenFromPlayer.addAll(CardPool.fromCardList(Lists.newArrayList(lines)));
        }
        if (data.containsKey("takenCopies")) takenCopies.load(data.readSubData("takenCopies"));
        takenCopies.reconcile(cardsTakenFromPlayer);
    }

    @Override
    public SaveFileData save() {
        SaveFileData data = new SaveFileData();
        data.store("id", id);
        data.store("name", name);
        data.store("baseEnemyName", baseEnemyName);
        data.store("playerWins", playerWins);
        data.store("playerLosses", playerLosses);
        data.store("createdAt", createdAt);
        String list = cardsTakenFromPlayer.toCardList("\n");
        data.storeObject("cardsTaken", list.isEmpty() ? new String[0] : list.split("\n"));
        takenCopies.reconcile(cardsTakenFromPlayer);
        data.store("takenCopies", takenCopies.save());
        return data;
    }

    @Override
    public String toString() {
        return name + " (" + baseEnemyName + ", " + record() + ")";
    }
}
