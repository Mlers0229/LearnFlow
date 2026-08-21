import json

from app.config import llm_runtime


def test_runtime_file_secret_is_removed_and_environment_secret_wins(tmp_path, monkeypatch):
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
