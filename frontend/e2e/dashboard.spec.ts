import { test, expect } from "@playwright/test";

test("quotes, both agents and persisted history", async ({
  page,
}, testInfo) => {
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto("/");
  await expect(
    page.getByRole("heading", { name: "Observatório" }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "EURUSD", exact: false }),
  ).toBeVisible();
  await expect(page.getByText("Dados:")).toContainText("Simulados");
  for (const name of ["Observador de mercado", "Revisor de risco"]) {
    const button = page.getByRole("button", { name: `Analisar com ${name}` });
    await expect(button).toBeEnabled();
    await button.click();
    await expect(button).toBeEnabled();
  }
  await page.reload();
  await expect(page.getByText("Simulação · sem LLM").first()).toBeVisible();
  await page.getByText("Sugestão de melhoria").first().click();
  await expect(page.getByText("Versão usada · SHA-256").first()).toBeVisible();
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
  expect(errors).toEqual([]);
  await page.screenshot({
    path: testInfo.outputPath("dashboard.png"),
    fullPage: true,
  });
});

test("empty market disables analysis", async ({ page }) => {
  await page.route("**/api/market/ticks", (route) =>
    route.fulfill({ json: [] }),
  );
  await page.goto("/");
  await expect(page.getByText("Aguardando cotações da bridge.")).toBeVisible();
  await expect(
    page.getByRole("button", { name: "Analisar com Observador de mercado" }),
  ).toBeDisabled();
});

test("unavailable backend is visible and recovers", async ({ page }) => {
  await page.route("**/api/**", (route) =>
    route.fulfill({ status: 503, json: { detail: "Backend indisponível" } }),
  );
  await page.goto("/");
  await expect(page.getByRole("alert")).toHaveText("Backend indisponível");
  await page.unroute("**/api/**");
  await page.getByRole("button", { name: "Atualizar" }).click();
  await expect(
    page.getByRole("cell", { name: "EURUSD", exact: false }),
  ).toBeVisible();
  await expect(page.getByRole("alert")).toHaveCount(0);
});

test("failed analysis shows an error and allows retry", async ({ page }) => {
  await page.route("**/api/agents/*/runs", (route) =>
    route.fulfill({
      status: 502,
      json: { detail: "Análise falhou; consulte o histórico" },
    }),
  );
  await page.goto("/");
  const button = page.getByRole("button", {
    name: "Analisar com Observador de mercado",
  });
  await expect(button).toBeEnabled();
  await button.click();
  await expect(page.getByRole("alert")).toHaveText(
    "Análise falhou; consulte o histórico",
  );
  await expect(button).toBeEnabled();
});
