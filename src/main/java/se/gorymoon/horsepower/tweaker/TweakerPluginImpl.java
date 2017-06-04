package se.gorymoon.horsepower.tweaker;

import com.google.common.collect.Lists;
import minetweaker.IUndoableAction;
import minetweaker.MineTweakerAPI;
import minetweaker.MineTweakerImplementationAPI;
import minetweaker.api.item.IIngredient;
import minetweaker.api.item.IItemStack;
import minetweaker.api.minecraft.MineTweakerMC;
import minetweaker.util.IEventHandler;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;
import se.gorymoon.horsepower.recipes.GrindstoneRecipe;
import se.gorymoon.horsepower.recipes.GrindstoneRecipes;
import stanhebben.zenscript.annotations.ZenClass;
import stanhebben.zenscript.annotations.ZenMethod;

import java.util.List;

import static minetweaker.api.minecraft.MineTweakerMC.getItemStack;
import static minetweaker.api.minecraft.MineTweakerMC.getItemStacks;

@ZenClass("mods.horsepower")
public class TweakerPluginImpl implements ITweakerPlugin, IEventHandler<MineTweakerImplementationAPI.ReloadEvent> {

    public TweakerPluginImpl() {
        MineTweakerImplementationAPI.onReloadEvent(this);
    }

    private static List<IUndoableAction> actions = Lists.newArrayList();

    @Override
    public void applyTweaker() {
        for (IUndoableAction action: actions)
            action.apply();
    }

    @Override
    public void handle(MineTweakerImplementationAPI.ReloadEvent reloadEvent) {
        actions.clear();
    }

    @Override
    public void register() {
        MineTweakerAPI.registerClass(TweakerPluginImpl.class);
    }

    @ZenMethod
    public static void addGrindstoneRecipe(IIngredient input, IItemStack output, int time) {
        List<IItemStack> items = input.getItems();
        if(items == null) {
            MineTweakerAPI.logError("Cannot turn " + input.toString() + " into a furnace recipe");
        }

        ItemStack[] items2 = getItemStacks(items);
        ItemStack output2 = getItemStack(output);

        AddGrindstoneRecipe recipe = new AddGrindstoneRecipe(input, items2, output2, time);
        MineTweakerAPI.apply(recipe);
        actions.add(recipe);
    }

    @ZenMethod
    public static void removeGrindstoneReicpe(IIngredient output) {

        List<GrindstoneRecipe> toRemove = Lists.newArrayList();
        List<Integer> removeIndex = Lists.newArrayList();

        for (int i = 0; i < GrindstoneRecipes.instance().getGrindstoneRecipes().size(); i++) {
            GrindstoneRecipe recipe = GrindstoneRecipes.instance().getGrindstoneRecipes().get(i);
            if (OreDictionary.itemMatches(MineTweakerMC.getItemStack(output), recipe.getOutput(), false)) {
                toRemove.add(recipe);
                removeIndex.add(Integer.valueOf(i));
            }
        }
        RemoveGrindstoneRecipe recipe = new RemoveGrindstoneRecipe(toRemove, removeIndex);
        MineTweakerAPI.apply(recipe);
        actions.add(recipe);
    }

    private static class AddGrindstoneRecipe implements IUndoableAction {

        private final IIngredient ingredient;
        private final ItemStack[] input;
        private final ItemStack output;
        private final int time;

        public AddGrindstoneRecipe(IIngredient ingredient, ItemStack[] inputs, ItemStack output2, int time) {
            this.ingredient = ingredient;
            this.input = inputs;
            this.output = output2;
            this.time = time;
        }

        @Override
        public void apply() {
            for (ItemStack stack: input) {
                GrindstoneRecipe recipe = new GrindstoneRecipe(stack, output, time);
                GrindstoneRecipes.instance().addGrindstoneRecipe(recipe);
                MineTweakerAPI.getIjeiRecipeRegistry().addRecipe(recipe, "horsepower.grinding");
            }
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public void undo() {
            for (ItemStack stack: input) {
                GrindstoneRecipe recipe = new GrindstoneRecipe(stack, output, time);
                GrindstoneRecipes.instance().removeGrindstoneRecipe(recipe);
                MineTweakerAPI.getIjeiRecipeRegistry().removeRecipe(recipe, "horsepower.grinding");
            }
        }

        @Override
        public String describe() {
            return "Adding grindstone recipe for " + ingredient;
        }

        @Override
        public String describeUndo() {
            return "Removing grindstone recipe for " + ingredient;
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }

    private static class RemoveGrindstoneRecipe implements IUndoableAction {
        private final List<Integer> removingIndices;
        private final List<GrindstoneRecipe> recipes;

        private RemoveGrindstoneRecipe(List<GrindstoneRecipe> recipes, List<Integer> removingIndices) {
            this.recipes = recipes;
            this.removingIndices = removingIndices;
        }

        @Override
        public void apply() {
            for(int i = this.removingIndices.size() - 1; i >= 0; --i) {
                GrindstoneRecipes.instance().getGrindstoneRecipes().remove(removingIndices.get(i).intValue());
                MineTweakerAPI.getIjeiRecipeRegistry().removeRecipe(recipes.get(i), "horsepower.grinding");
            }
        }

        @Override
        public boolean canUndo() {
            return true;
        }

        @Override
        public void undo() {
            for(int i = 0; i < this.removingIndices.size(); ++i) {
                int index = Math.min(GrindstoneRecipes.instance().getGrindstoneRecipes().size(), this.removingIndices.get(i).intValue());
                GrindstoneRecipes.instance().getGrindstoneRecipes().add(index, recipes.get(i));
                MineTweakerAPI.getIjeiRecipeRegistry().addRecipe(recipes.get(i), "horsepower.grinding");
            }
        }

        @Override
        public String describe() {
            return "Removing " + recipes.size() + " grindstone recipes";
        }

        @Override
        public String describeUndo() {
            return "Restoring " + recipes.size() + " grindstone recipes";
        }

        @Override
        public Object getOverrideKey() {
            return null;
        }
    }

}
