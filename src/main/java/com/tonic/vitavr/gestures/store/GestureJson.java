package com.tonic.vitavr.gestures.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.tonic.vitavr.gestures.model.steps.AbstractStep;
import com.tonic.vitavr.gestures.model.steps.ItemOpStep;
import com.tonic.vitavr.gestures.model.steps.ItemSubOpStep;
import com.tonic.vitavr.gestures.model.steps.OpenTabStep;
import com.tonic.vitavr.gestures.model.steps.PrayerStep;
import com.tonic.vitavr.gestures.model.steps.QuickPrayersStep;
import com.tonic.vitavr.gestures.model.steps.RunStep;
import com.tonic.vitavr.gestures.model.steps.SpecialStep;
import com.tonic.vitavr.gestures.model.steps.SpellStep;
import com.tonic.vitavr.gestures.model.steps.SwitchProfileStep;
import com.tonic.vitavr.gestures.model.steps.UnequipStep;
import com.tonic.vitavr.gestures.model.steps.UseOnStep;
import com.tonic.vitavr.gestures.model.steps.WaitStep;
import com.tonic.vitavr.gestures.model.steps.WearSetStep;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The gesture files' JSON, with each step written with a "type" naming its kind.
 */
public final class GestureJson
{
    private static final String TYPE = "type";
    private static final Map<String, Class<? extends AbstractStep>> STEPS = new LinkedHashMap<>();

    static
    {
        STEPS.put("itemOp", ItemOpStep.class);
        STEPS.put("itemSubOp", ItemSubOpStep.class);
        STEPS.put("wearSet", WearSetStep.class);
        STEPS.put("unequip", UnequipStep.class);
        STEPS.put("prayer", PrayerStep.class);
        STEPS.put("quickPrayers", QuickPrayersStep.class);
        STEPS.put("spell", SpellStep.class);
        STEPS.put("useOn", UseOnStep.class);
        STEPS.put("special", SpecialStep.class);
        STEPS.put("run", RunStep.class);
        STEPS.put("openTab", OpenTabStep.class);
        STEPS.put("switchProfile", SwitchProfileStep.class);
        STEPS.put("wait", WaitStep.class);
    }

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().registerTypeAdapterFactory(new StepFactory()).create();

    private GestureJson()
    {
    }

    /**
     * @param value What to copy.
     * @param type Its class.
     * @param <T> Its type.
     * @return A copy sharing nothing with it.
     */
    public static <T> T copy(T value, Class<T> type)
    {
        return GSON.fromJson(GSON.toJsonTree(value), type);
    }

    private static final class StepFactory implements TypeAdapterFactory
    {
        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type)
        {
            if (type.getRawType() != AbstractStep.class)
            {
                return null;
            }
            Map<String, TypeAdapter<? extends AbstractStep>> byName = new LinkedHashMap<>();
            Map<Class<?>, String> names = new LinkedHashMap<>();
            for (Map.Entry<String, Class<? extends AbstractStep>> step : STEPS.entrySet())
            {
                byName.put(step.getKey(), gson.getDelegateAdapter(this, TypeToken.get(step.getValue())));
                names.put(step.getValue(), step.getKey());
            }
            TypeAdapter<JsonElement> elements = gson.getAdapter(JsonElement.class);
            return (TypeAdapter<T>) new TypeAdapter<AbstractStep>()
            {
                @Override
                public void write(JsonWriter out, AbstractStep step) throws IOException
                {
                    if (step == null)
                    {
                        out.nullValue();
                        return;
                    }
                    String name = names.get(step.getClass());
                    if (name == null)
                    {
                        throw new JsonParseException("No step type for " + step.getClass().getName());
                    }
                    JsonObject tree = writeTree(byName.get(name), step).getAsJsonObject();
                    JsonObject typed = new JsonObject();
                    typed.addProperty(TYPE, name);
                    for (Map.Entry<String, JsonElement> field : tree.entrySet())
                    {
                        typed.add(field.getKey(), field.getValue());
                    }
                    elements.write(out, typed);
                }

                @Override
                public AbstractStep read(JsonReader in) throws IOException
                {
                    JsonElement tree = elements.read(in);
                    if (tree == null || tree.isJsonNull())
                    {
                        return null;
                    }
                    JsonElement type = tree.getAsJsonObject().get(TYPE);
                    TypeAdapter<? extends AbstractStep> adapter = type == null ? null : byName.get(type.getAsString());
                    if (adapter == null)
                    {
                        throw new JsonParseException("Unknown step type " + type);
                    }
                    return adapter.fromJsonTree(tree);
                }
            };
        }

        @SuppressWarnings("unchecked")
        private static <S extends AbstractStep> JsonElement writeTree(TypeAdapter<S> adapter, AbstractStep step)
        {
            return adapter.toJsonTree((S) step);
        }
    }
}
