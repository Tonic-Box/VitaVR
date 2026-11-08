package com.tonic.vitavr.gestures.store;

import com.tonic.vitavr.gestures.model.Binding;
import com.tonic.vitavr.gestures.model.Condition;
import com.tonic.vitavr.gestures.model.Gesture;
import com.tonic.vitavr.gestures.model.GestureKind;
import com.tonic.vitavr.gestures.model.ItemRef;
import com.tonic.vitavr.gestures.model.Profile;
import com.tonic.vitavr.gestures.model.Sample;
import com.tonic.vitavr.gestures.model.Take;
import com.tonic.vitavr.gestures.model.steps.ItemOpStep;
import com.tonic.vitavr.gestures.model.steps.PrayerStep;
import com.tonic.vitavr.gestures.model.steps.WaitStep;
import com.tonic.vitavr.gestures.model.steps.WearSetStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GestureStoreTest
{
    @TempDir
    Path folder;

    @Test
    void aSavedLibraryAndProfilesReadBackTheSame()
    {
        List<String> activeSaved = new ArrayList<>();
        GestureStore store = new GestureStore(folder, "", activeSaved::add);
        store.load();
        Gesture shape = shape("Zed");
        store.update(gestures ->
        {
            gestures.getLibrary().add(shape);
            Profile pvp = Profile.named("PvP");
            pvp.getBindings().put(shape.getId(), binding());
            gestures.getProfiles().add(pvp);
            gestures.setActive("PvP");
        });

        GestureStore again = new GestureStore(folder, "PvP", name -> { });
        again.load();

        Gestures read = again.snapshot();
        assertEquals(store.snapshot(), read);
        assertEquals(List.of("PvP"), activeSaved);
        Binding bound = read.binding(shape.getId());
        assertInstanceOf(ItemOpStep.class, bound.getSteps().get(0));
        assertInstanceOf(WaitStep.class, bound.getSteps().get(1));
        assertInstanceOf(WearSetStep.class, bound.getSteps().get(2));
        assertInstanceOf(PrayerStep.class, bound.getSteps().get(3));
        assertEquals(3, read.gesture(shape.getId()).getTakes().get(0).getRight().size());
    }

    @Test
    void anEmptyStoreHasTheDefaultProfileInUse()
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();

        assertEquals(Gestures.DEFAULT_PROFILE, store.snapshot().activeProfile().getName());
    }

    @Test
    void aFileThatCannotBeReadIsPutAsideAndTheRestLoads() throws IOException
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();
        store.update(gestures -> gestures.getProfiles().add(Profile.named("Bossing")));
        Path broken = folder.resolve("profiles").resolve("Bossing.json");
        Files.write(broken, "{ not json".getBytes(StandardCharsets.UTF_8));

        GestureStore again = new GestureStore(folder, "", name -> { });
        again.load();

        assertNull(again.snapshot().profile("Bossing"));
        assertTrue(Files.exists(folder.resolve("profiles").resolve("Bossing.json.bad")));
        assertFalse(again.getProblem().isEmpty());
        assertEquals(Gestures.DEFAULT_PROFILE, again.snapshot().activeProfile().getName());
    }

    @Test
    void aChangeLeavesTheSnapshotAReaderHoldsAlone()
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();
        Gestures held = store.snapshot();

        store.update(gestures -> gestures.getLibrary().add(shape("New")));

        assertTrue(held.getLibrary().isEmpty());
        assertEquals(1, store.snapshot().getLibrary().size());
    }

    @Test
    void bindingsOfADeletedGestureGoWithIt()
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();
        Gesture shape = shape("Gone");
        store.update(gestures ->
        {
            gestures.getLibrary().add(shape);
            gestures.activeProfile().getBindings().put(shape.getId(), binding());
        });

        store.update(gestures -> gestures.getLibrary().removeIf(gesture -> gesture.getId().equals(shape.getId())));

        assertTrue(store.snapshot().activeProfile().getBindings().isEmpty());
    }

    @Test
    void undoPutsBackTheLastChange()
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();
        store.update(gestures -> gestures.getLibrary().add(shape("Kept")));
        store.update(gestures -> gestures.getLibrary().clear());

        assertTrue(store.undo());

        assertEquals("Kept", store.snapshot().getLibrary().get(0).getName());
        assertFalse(store.undo());
    }

    @Test
    void aRemovedProfilesFileIsDeleted()
    {
        GestureStore store = new GestureStore(folder, "", name -> { });
        store.load();
        store.update(gestures -> gestures.getProfiles().add(Profile.named("Old")));
        assertTrue(Files.exists(folder.resolve("profiles").resolve("Old.json")));

        store.update(gestures -> gestures.getProfiles().removeIf(profile -> profile.getName().equals("Old")));

        assertFalse(Files.exists(folder.resolve("profiles").resolve("Old.json")));
    }

    @Test
    void profileNamesBecomeSafeDistinctFileNames()
    {
        Set<Path> taken = new HashSet<>();
        taken.add(folder.resolve("A_B.json"));

        assertEquals("A_B 2.json", GestureStore.fileName("A/B", taken));
        assertEquals("___.json", GestureStore.fileName("???", new HashSet<>()));
        assertEquals("profile.json", GestureStore.fileName("   ", new HashSet<>()));
    }

    private static Gesture shape(String name)
    {
        Gesture gesture = new Gesture();
        gesture.setName(name);
        gesture.setKind(GestureKind.SHAPE);
        Take take = new Take();
        take.getRight().add(new Sample(0, 0, 0, 0.3, 0, 0, 0, 1));
        take.getRight().add(new Sample(0.1, 0.2, 0, 0.3, 0, 0, 0, 1));
        take.getRight().add(new Sample(0.2, 0, -0.2, 0.3, 0, 0, 0, 1));
        gesture.getTakes().add(take);
        return gesture;
    }

    private static Binding binding()
    {
        Binding binding = new Binding();
        ItemOpStep eat = new ItemOpStep();
        eat.setItem(ItemRef.group("Eat"));
        binding.getSteps().add(eat);
        binding.getSteps().add(new WaitStep());
        WearSetStep wear = new WearSetStep();
        wear.getItems().add(ItemRef.named(ItemRef.Match.VARIANTS, "Ring of dueling"));
        binding.getSteps().add(wear);
        PrayerStep prayer = new PrayerStep();
        prayer.getPrayers().add("PROTECT_FROM_MAGIC");
        prayer.setMode(PrayerStep.Mode.ONLY);
        binding.getSteps().add(prayer);
        Condition low = new Condition();
        low.setKind(Condition.Kind.HP_PERCENT_AT_MOST);
        low.setValue(40);
        binding.getConditions().add(low);
        return binding;
    }
}
