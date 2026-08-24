import json

from app.config import llm_runtime


def test_runtime_file_secret_is_removed_and_environment_secret_wins(tmp_path, monkeypatch):
    monkeypatch.delenv("LEARNFLOW_LLM_RUNTIME_FILE", raising=False)
    runtime_file = tmp_path / "llm_runtime.json"
    runtime_file.write_text(
        json.dumps({"apiKey": "legacy-file-secret", "defaultModel": "test-model"}),
        encoding="utf-8",
    )
    monkeypatch.setattr(llm_runtime, "_RUNTIME_FILE", runtime_file)
    monkeypatch.setenv("LLM_API_KEY", "environment-secret")

    config = llm_runtime.get_effective_llm_config()
    persisted = json.loads(runtime_file.read_text(encoding="utf-8"))

    assert config["apiKey"] == "environment-secret"
    assert config["source"]["apiKey"] == "environment"
    assert "apiKey" not in persisted


def test_runtime_file_path_can_be_configured(tmp_path, monkeypatch):
    runtime_file = tmp_path / "runtime" / "llm_runtime.json"
    monkeypatch.setenv("LEARNFLOW_LLM_RUNTIME_FILE", str(runtime_file))

    saved = llm_runtime.save_runtime_config(
        {
            "apiBase": "https://api.deepseek.com",
            "defaultModel": "deepseek-chat",
            "apiKey": "must-not-be-persisted",
        }
    )

    persisted = json.loads(runtime_file.read_text(encoding="utf-8"))

    assert saved["apiBase"] == "https://api.deepseek.com"
    assert persisted["defaultModel"] == "deepseek-chat"
    assert "apiKey" not in persisted
