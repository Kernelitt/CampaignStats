package kernelit;

import arc.Events;
import arc.scene.ui.layout.Table;
import arc.struct.ObjectIntMap;
import arc.Core;
import arc.Events;
import arc.util.Log;
import arc.struct.Seq;
import arc.struct.ObjectFloatMap;

import mindustry.type.Item;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.ui.dialogs.PlanetDialog;
import mindustry.Vars;
import mindustry.game.CampaignStats;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.mod.Mod;
import mindustry.type.Planet;
import mindustry.type.UnitType;
import mindustry.ui.dialogs.BaseDialog;
import mindustry.world.Block;
import mindustry.type.Sector;

public class CampaignStatsMod extends Mod {

    @Override
    public void init(){
        Log.info("[CampaignStats] mod init");

        Events.on(ClientLoadEvent.class, e -> {
            Log.info("[CampaignStats] client load");

            PlanetDialog planet = Vars.ui.planet;

            planet.shown(() -> {
                planet.buttons.getChildren().forEach(child -> {
                    if(child instanceof arc.scene.ui.TextButton && ((arc.scene.ui.TextButton)child).getText().toString().equals("Статистика")){
                        child.remove();
                    }
                });

                planet.buttons.button("Статистика", this::showStats).pad(4);
            });
        });
    }


    private Planet selectedPlanet;
    private String selectedTab = "overview";

    private void showStats(){
        BaseDialog dialog = new BaseDialog(bundle("stats.title"));
        dialog.addCloseButton();

        if(selectedPlanet == null){
            selectedPlanet = firstPlanetWithStats();
        }

        Table planetButtons = new Table();
        Table tabButtons = new Table();
        Table content = new Table();

        rebuildPlanetButtons(planetButtons, content);
        rebuildTabs(tabButtons, content);
        rebuildContent(content);

        dialog.cont.table(top -> {
            top.add(planetButtons).growX().row();
            top.add(tabButtons).growX().padTop(8).row();
        }).growX().row();

        dialog.cont.pane(content)
            .grow()
            .fill()
            .expand()
            .padTop(8)
            .row();

        dialog.show();
    }

    private String bundle(String key){
        if(!Core.bundle.has(key)){
            Log.warn("[CampaignStats] Missing bundle key: @", key);
        }

        return Core.bundle.get(key, key);
    }

    private Planet firstPlanetWithStats(){
        for(Planet planet : Vars.content.planets()){
            if(planet.stats() != null){
                return planet;
            }
        }
        return Vars.content.planets().first();
    }

    private void rebuildPlanetButtons(Table table, Table content){
        table.clear();
        table.add(bundle("stats.planet")).padRight(8);

        for(Planet planet : Vars.content.planets()){
            if(planet.stats() == null) continue;

            table.button(planet.localizedName, () -> {
                selectedPlanet = planet;
                rebuildPlanetButtons(table, content);
                rebuildContent(content);
            }).pad(2);
        }
    }

    private void rebuildTabs(Table table, Table content){
        table.clear();

        addTab(table, content, "overview", "stats.tab.overview");
        addTab(table, content, "units", "stats.tab.units");
        addTab(table, content, "blocks", "stats.tab.blocks");
        addTab(table, content, "export", "stats.tab.export");
    }

    private void addTab(Table table, Table content, String tab, String key){
        table.button(bundle(key), () -> {
            selectedTab = tab;
            rebuildTabs(table, content);
            rebuildContent(content);
        }).pad(2);
    }

    private void rebuildContent(Table content){
        content.clear();
        content.left().top();

        if(selectedPlanet == null){
            content.add(bundle("stats.noPlanet")).left();
            return;
        }

        CampaignStats stats = selectedPlanet.stats();

        content.add("[accent]" + selectedPlanet.localizedName + "[]")
            .left()
            .padBottom(8)
            .row();

        switch(selectedTab){
            case "units" -> {
                addUnitMap(content, "stats.units.produced", stats.unitsProduced);
                addUnitMap(content, "stats.units.destroyed", stats.unitsDestroyed);
                addUnitMap(content, "stats.units.enemyDestroyed", stats.enemyUnitsDestroyed);
            }
            case "blocks" -> {
                addBlockMap(content, "stats.blocks.built", stats.buildingsBuilt);
                addBlockMap(content, "stats.blocks.deconstructed", stats.buildingsDeconstructed);
                addBlockMap(content, "stats.blocks.destroyed", stats.buildingsDestroyed);
                addBlockMap(content, "stats.blocks.enemyDestroyed", stats.enemyBuildingsDestroyed);
            }
            case "export" -> addExportMap(content, selectedPlanet);
            default -> addSummary(content, selectedPlanet, stats);
        }
    }

    private void addExportMap(Table t, Planet planet){
        t.add("[yellow]" + bundle("stats.export") + "[]")
            .left()
            .padBottom(8)
            .colspan(2)
            .row();

        ObjectFloatMap<Item> totals = new ObjectFloatMap<>();

        for(Sector sector : planet.sectors){
            if(sector.info == null) continue;

            for(ObjectFloatMap.Entry<Item> entry : sector.info.exportRates().entries()){
                totals.put(entry.key, totals.get(entry.key, 0f) + entry.value);
            }
        }

        if(totals.size == 0){
            t.add("[lightgray]" + bundle("stats.export.none") + "[]")
                .left()
                .colspan(2)
                .row();
            return;
        }

        Seq<ObjectFloatMap.Entry<Item>> entries = new Seq<>();
        for(ObjectFloatMap.Entry<Item> entry : totals.entries()){
            ObjectFloatMap.Entry<Item> newEntry = new ObjectFloatMap.Entry<>();
            newEntry.key = entry.key;
            newEntry.value = entry.value;
            entries.add(newEntry);
        }
        
        entries.sort((a, b) -> Float.compare(b.value, a.value));

        for(ObjectFloatMap.Entry<Item> entry : entries){
            // Рассчитываем экспорт в минуту (в секунду * 60)
            float ratePerMinute = entry.value * 60f;

            // Проверяем минимальный порог уже для минутного значения
            if(ratePerMinute <= 0.05f) continue;

            String name = entry.key.localizedName;
            // Добавляем "/мин" к значению для наглядности (можно настроить под ваш bundle)
            String value = String.format("%.1f", ratePerMinute);

            addRow(t, name, value);
        }
    }



    private void addSummary(Table t, Planet planet, CampaignStats s){
        int ownedSectors = 0;

        for(Sector sector : planet.sectors){
            if(sector.hasBase()){
                ownedSectors++;
            }
        }

        addRow(t, "stats.sectors", ownedSectors + " / " + planet.sectors.size);
        addRow(t, "stats.playtime", formatTime(s.playtime));
        addRow(t, "stats.waves", String.valueOf(s.wavesLasted));
        addRow(t, "stats.captured", String.valueOf(s.sectorsCaptured));
        addRow(t, "stats.lost", String.valueOf(s.sectorsLost));
    }

    private void addUnitMap(Table t, String titleKey, ObjectIntMap<UnitType> map){
        t.add("[yellow]" + bundle(titleKey) + "[]")
            .left()
            .padTop(12)
            .colspan(2)
            .row();

        if(map.size == 0){
            t.add("[lightgray]" + bundle("stats.none") + "[]")
                .left()
                .colspan(2)
                .row();
            return;
        }

        Seq<ObjectIntMap.Entry<UnitType>> entries = map.entries().toArray();
        entries.sort((a, b) -> Integer.compare(b.value, a.value));

        for(ObjectIntMap.Entry<UnitType> entry : entries){
            String name = entry.key == null ? "?" : entry.key.localizedName;
            addRow(t, name, String.valueOf(entry.value));
        }
    }

    private void addBlockMap(Table t, String titleKey, ObjectIntMap<Block> map){
        t.add("[yellow]" + bundle(titleKey) + "[]")
            .left()
            .padTop(12)
            .colspan(2)
            .row();

        if(map.size == 0){
            t.add("[lightgray]" + bundle("stats.none") + "[]")
                .left()
                .colspan(2)
                .row();
            return;
        }

        Seq<ObjectIntMap.Entry<Block>> entries = map.entries().toArray();
        entries.sort((a, b) -> Integer.compare(b.value, a.value));

        for(ObjectIntMap.Entry<Block> entry : entries){
            String name = entry.key == null ? "?" : entry.key.localizedName;
            addRow(t, name, String.valueOf(entry.value));
        }
    }

    private void addRow(Table t, String name, String value){
        t.add(bundle(name)).left().growX();
        t.add("[accent]" + value + "[]").right().row();
    }

    private String formatTime(long millis){
        long totalSeconds = millis / 1000L;
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;

        if(hours > 0){
            return hours + " h " + minutes + " min";
        }
        return minutes + " min";
    }
}