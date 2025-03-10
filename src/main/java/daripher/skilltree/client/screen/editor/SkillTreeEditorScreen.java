package daripher.skilltree.client.screen.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Predicates;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.math.Vector3f;

import daripher.skilltree.client.SkillTreeClientData;
import daripher.skilltree.client.screen.ScreenHelper;
import daripher.skilltree.client.widget.SkillButton;
import daripher.skilltree.client.widget.SkillConnection;
import daripher.skilltree.client.widget.StatsList;
import daripher.skilltree.skill.PassiveSkill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Widget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class SkillTreeEditorScreen extends Screen {
	protected final Map<ResourceLocation, SkillButton> skillButtons = new HashMap<>();
	private final List<SkillConnection> skillConnections = new ArrayList<>();
	private final List<SkillConnection> gatewayConnections = new ArrayList<>();
	private final ResourceLocation treeId;
	private int prevMouseX;
	private int prevMouseY;
	private float zoom = 1F;
	protected double scrollSpeedX;
	protected double scrollSpeedY;
	protected double scrollX;
	protected double scrollY;
	protected int maxScrollX;
	protected int maxScrollY;

	public SkillTreeEditorScreen(ResourceLocation skillTreeId) {
		super(Component.empty());
		this.treeId = skillTreeId;
		this.minecraft = Minecraft.getInstance();
	}

	@Override
	public void init() {
		clearWidgets();
		addSkillButtons();
		maxScrollX -= width / 2 - 80;
		maxScrollY -= height / 2 - 80;
		if (maxScrollX < 0) maxScrollX = 0;
		if (maxScrollY < 0) maxScrollY = 0;
		addSkillConnections();
	}

	@Override
	public void render(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		updateScreen(partialTick);
		renderBackground(poseStack);
		renderConnections(poseStack, mouseX, mouseY, partialTick);
		renderSkills(poseStack, mouseX, mouseY, partialTick);
		renderOverlay(poseStack, mouseX, mouseY, partialTick);
		renderWidgets(poseStack, mouseX, mouseY, partialTick);
		renderSkillTooltip(poseStack, mouseX, mouseY, partialTick);
		prevMouseX = mouseX;
		prevMouseY = mouseY;
	}

	private void renderWidgets(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		for (Widget widget : renderables) {
			if (widget instanceof SkillButton) continue;
			widget.render(poseStack, mouseX, mouseY, partialTick);
		}
	}

	private void renderSkillTooltip(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		if (getWidgetAt(mouseX, mouseY).isPresent()) return;
		getSkillAt(mouseX, mouseY).ifPresent(button -> {
			renderSkillTooltip(button, poseStack, mouseX + (prevMouseX - mouseX) * partialTick, mouseY + (prevMouseY - mouseY) * partialTick);
		});
	}

	private void renderSkills(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		poseStack.pushPose();
		poseStack.translate(scrollX, scrollY, 0);
		for (SkillButton widget : skillButtons.values()) {
			poseStack.pushPose();
			poseStack.translate(widget.x + widget.getWidth() / 2, widget.y + widget.getHeight() / 2, 0F);
			poseStack.scale(zoom, zoom, 1F);
			poseStack.translate(-widget.x - widget.getWidth() / 2, -widget.y - widget.getHeight() / 2, 0F);
			widget.render(poseStack, mouseX, mouseY, partialTick);
			poseStack.popPose();
		}
		poseStack.popPose();
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		Optional<GuiEventListener> widget = getWidgetAt(mouseX, mouseY);
		if (widget.isPresent()) return widget.get().mouseClicked(mouseX, mouseY, button);
		Optional<SkillButton> skill = getSkillAt(mouseX, mouseY);
		if (skill.isPresent()) return skill.get().mouseClicked(mouseX - scrollX, mouseY - scrollY, button);
		return false;
	}

	public Optional<GuiEventListener> getWidgetAt(double mouseX, double mouseY) {
		return super.getChildAt(mouseX, mouseY).filter(Predicates.not(SkillButton.class::isInstance));
	}

	public Optional<SkillButton> getSkillAt(double mouseX, double mouseY) {
		mouseX -= scrollX;
		mouseY -= scrollY;
		for (SkillButton button : skillButtons.values()) {
			double skillSize = button.skill.getButtonSize() * zoom;
			double skillX = button.x + button.getWidth() / 2 - skillSize / 2;
			double skillY = button.y + button.getHeight() / 2 - skillSize / 2;
			if (mouseX >= skillX && mouseY >= skillY && mouseX < skillX + skillSize && mouseY < skillY + skillSize) {
				return Optional.of(button);
			}
		}
		return Optional.empty();
	}

	public void addSkillButtons() {
		skillButtons.clear();
		SkillTreeClientData.getOrCreateEditorTree(treeId).forEach(this::addSkillButton);
	}

	protected void addSkillButton(ResourceLocation skillId, PassiveSkill skill) {
		float skillX = skill.getPositionX();
		float skillY = skill.getPositionY();
		double buttonX = skillX + width / 2F + (skillX + skill.getButtonSize() / 2) * (zoom - 1);
		double buttonY = skillY + height / 2F + (skillY + skill.getButtonSize() / 2) * (zoom - 1);
		SkillButton button = new SkillButton(() -> 0F, buttonX, buttonY, skill, this::buttonPressed);
		addRenderableWidget(button);
		skillButtons.put(skillId, button);
		if (maxScrollX < Mth.abs(skillX)) maxScrollX = (int) Mth.abs(skillX);
		if (maxScrollY < Mth.abs(skillY)) maxScrollY = (int) Mth.abs(skillY);
	}

	public void addSkillConnections() {
		skillConnections.clear();
		gatewayConnections.clear();
		Map<ResourceLocation, PassiveSkill> skills = SkillTreeClientData.getSkillsForTree(treeId);
		skills.forEach((skillId1, skill) -> {
			skill.getConnectedSkills().forEach(skillId2 -> {
				connectSkills(skillConnections, skillId1, skillId2);
			});
		});
		Map<ResourceLocation, List<PassiveSkill>> gateways = new HashMap<ResourceLocation, List<PassiveSkill>>();
		skills.values().stream().filter(PassiveSkill::isGateway).forEach(skill -> {
			ResourceLocation gatewayId = skill.getGatewayId().get();
			if (!gateways.containsKey(gatewayId)) {
				gateways.put(gatewayId, new ArrayList<>());
			}
			gateways.get(gatewayId).add(skill);
		});
		gateways.forEach((gatewayId, list) -> {
			for (int i = 1; i < list.size(); i++) {
				connectSkills(gatewayConnections, list.get(0).getId(), list.get(i).getId());
			}
		});
	}

	protected void connectSkills(List<SkillConnection> connections, ResourceLocation skillId1, ResourceLocation skillId2) {
		SkillButton button1 = skillButtons.get(skillId1);
		SkillButton button2 = skillButtons.get(skillId2);
		connections.add(new SkillConnection(button1, button2));
	}

	public void renderSkillTooltip(SkillButton button, PoseStack poseStack, float mouseX, float mouseY) {
		List<MutableComponent> tooltip = button.getTooltip();
		if (tooltip.isEmpty()) return;
		int tooltipWidth = 0;
		int tooltipHeight = tooltip.size() == 1 ? 8 : 10;
		for (MutableComponent component : tooltip) {
			int k = font.width(component);
			if (k > tooltipWidth) tooltipWidth = k;
			tooltipHeight += font.lineHeight;
		}
		tooltipWidth += 42;
		float tooltipX = mouseX + 12;
		float tooltipY = mouseY - 12;
		if (tooltipX + tooltipWidth > width) {
			tooltipX -= 28 + tooltipWidth;
		}
		if (tooltipY + tooltipHeight + 6 > height) {
			tooltipY = height - tooltipHeight - 6;
		}
		poseStack.pushPose();
		poseStack.translate(tooltipX, tooltipY, 0);
		float zOffset = itemRenderer.blitOffset;
		itemRenderer.blitOffset = 400.0F;
		fill(poseStack, 1, 4, tooltipWidth - 1, tooltipHeight + 4, 0xDD000000);
		RenderSystem.enableTexture();
		MultiBufferSource.BufferSource buffer = MultiBufferSource.immediate(Tesselator.getInstance().getBuilder());
		poseStack.translate(0.0D, 0.0D, 400.0D);
		int textX = 5;
		int textY = 2;
		ScreenHelper.prepareTextureRendering(button.skill.getBorderTexture());
		blit(poseStack, -4, -4, 0, 0, 21, 20, 110, 20);
		blit(poseStack, tooltipWidth + 4 - 21, -4, -21, 0, 21, 20, 110, 20);
		int centerWidth = tooltipWidth + 8 - 42;
		int centerX = -4 + 21;
		while (centerWidth > 0) {
			int partWidth = Math.min(centerWidth, 68);
			blit(poseStack, centerX, -4, 21, 0, partWidth, 20, 110, 20);
			centerX += partWidth;
			centerWidth -= partWidth;
		}
		MutableComponent title = tooltip.remove(0);
		drawCenteredString(poseStack, font, title, tooltipWidth / 2, textY, 0xFFFFFF);
		textY += 17;
		for (MutableComponent component : tooltip) {
			font.draw(poseStack, component, textX, textY, 0xFFFFFF);
			textY += font.lineHeight;
		}
		buffer.endBatch();
		poseStack.popPose();
		itemRenderer.blitOffset = zOffset;
	}

	public void buttonPressed(Button button) {
		if (button instanceof SkillButton skillButton) skillButtonPressed(skillButton);
	}

	protected void skillButtonPressed(SkillButton button) {
	}

	private void updateScreen(float partialTick) {
		scrollX += scrollSpeedX * partialTick;
		scrollX = Math.max(-maxScrollX * zoom, Math.min(maxScrollX * zoom, scrollX));
		scrollSpeedX *= 0.8;
		scrollY += scrollSpeedY * partialTick;
		scrollY = Math.max(-maxScrollY * zoom, Math.min(maxScrollY * zoom, scrollY));
		scrollSpeedY *= 0.8;
	}

	private void renderOverlay(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		ScreenHelper.prepareTextureRendering(new ResourceLocation("skilltree:textures/screen/skill_tree_overlay.png"));
		blit(poseStack, 0, 0, 0, 0F, 0F, width, height, width, height);
	}

	@Override
	public void renderBackground(PoseStack poseStack) {
		ScreenHelper.prepareTextureRendering(new ResourceLocation("skilltree:textures/screen/skill_tree_background.png"));
		poseStack.pushPose();
		poseStack.translate(scrollX / 3F, scrollY / 3F, 0);
		int size = getBackgroundSize();
		blit(poseStack, (width - size) / 2, (height - size) / 2, 0, 0F, 0F, size, size, size, size);
		poseStack.popPose();
	}

	@Override
	public boolean mouseDragged(double mouseX, double mouseY, int mouseButton, double dragAmountX, double dragAmountY) {
		if (mouseButton != 0 && mouseButton != 2) return false;
		if (maxScrollX > 0) scrollSpeedX += dragAmountX * 0.25;
		if (maxScrollY > 0) scrollSpeedY += dragAmountY * 0.25;
		return true;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
		if (!getWidgetAt(mouseX, mouseY).filter(StatsList.class::isInstance).isPresent()) {
			if (amount > 0 && zoom < 2F) zoom += 0.05;
			if (amount < 0 && zoom > 0.25F) zoom -= 0.05;
			rebuildWidgets();
		}
		return super.mouseScrolled(mouseX, mouseY, amount);
	}

	protected void renderConnections(PoseStack poseStack, int mouseX, int mouseY, float partialTick) {
		ScreenHelper.prepareTextureRendering(new ResourceLocation("skilltree:textures/screen/skill_connection.png"));
		skillConnections.forEach(connection -> renderConnection(poseStack, connection));
		ScreenHelper.prepareTextureRendering(new ResourceLocation("skilltree:textures/screen/gateway_connection.png"));
		gatewayConnections.forEach(connection -> renderGatewayConnection(poseStack, connection, mouseX, mouseY));
	}

	private void renderGatewayConnection(PoseStack poseStack, SkillConnection connection, int mouseX, int mouseY) {
		SkillButton button1 = connection.getFirstButton();
		SkillButton button2 = connection.getSecondButton();
		Optional<SkillButton> hoveredSkill = getSkillAt(mouseX, mouseY);
		boolean hovered = !hoveredSkill.isEmpty() && (hoveredSkill.get() == button1 || hoveredSkill.get() == button2);
		if (hovered) {
			if (hoveredSkill.get() == button2) renderGatewayConnection(poseStack, button2, button1);
			else renderGatewayConnection(poseStack, button1, button2);
		}
	}

	private void renderGatewayConnection(PoseStack poseStack, SkillButton button1, SkillButton button2) {
		poseStack.pushPose();
		double connectionX = button1.x + button1.getWidth() / 2F;
		double connectionY = button1.y + button1.getHeight() / 2F;
		poseStack.translate(connectionX + scrollX, connectionY + scrollY, 0);
		float rotation = ScreenHelper.getAngleBetweenButtons(button1, button2);
		poseStack.mulPose(Vector3f.ZP.rotation(rotation));
		int length = (int) (ScreenHelper.getDistanceBetweenButtons(button1, button2) / zoom);
		poseStack.scale(zoom, zoom, 1F);
		blit(poseStack, 0, -3, length, 6, 0, 6, length, 6, 30, 12);
		poseStack.popPose();
	}

	private void renderConnection(PoseStack poseStack, SkillConnection connection) {
		poseStack.pushPose();
		SkillButton button1 = connection.getFirstButton();
		SkillButton button2 = connection.getSecondButton();
		double connectionX = button1.x + button1.getWidth() / 2F;
		double connectionY = button1.y + button1.getHeight() / 2F;
		poseStack.translate(connectionX + scrollX, connectionY + scrollY, 0);
		float rotation = ScreenHelper.getAngleBetweenButtons(button1, button2);
		poseStack.mulPose(Vector3f.ZP.rotation(rotation));
		int length = (int) ScreenHelper.getDistanceBetweenButtons(button1, button2);
		poseStack.scale(1F, zoom, 1F);
		blit(poseStack, 0, -3, length, 6, 0, 6, length, 6, 50, 12);
		poseStack.popPose();
	}

	protected int getBackgroundSize() {
		return 2048;
	}
}
