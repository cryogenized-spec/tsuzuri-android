# Tsuzuri — Speech providers and user-owned billing (November 2026)

This is the first, **small**, selectable provider-preset PR. It reuses the existing completed-recording multipart dictation client, encrypted BYOK slot and voice setup settings. It does not implement Google Cloud Speech-to-Text or realtime WebSocket streaming.

## Working file-transcription choices
- OpenAI **GPT-Transcribe** (`gpt-transcribe`): https://developers.openai.com/api/docs/models/gpt-transcribe — **$0.0045/min**. Create/manage project key: https://platform.openai.com/api-keys . Credits/billing are managed by the user in their API organisation; a ChatGPT Plus subscription is not API credit.
- Mistral **Voxtral Mini Transcribe 3** (`voxtral-mini-transcribe-3-0`): https://docs.mistral.ai/models/voxtral-mini-transcribe-3-0 — **$0.0035/min**. Mistral Studio: https://console.mistral.ai/ .
- OpenAI GPT-4o mini Transcribe (`gpt-4o-mini-transcribe`): **legacy only**, $0.003/min. OpenAI has announced shutdown on **2027-02-26**: https://developers.openai.com/api/docs/deprecations . Do not use as a recommended default.

The popup's presets set the matching HTTPS endpoint and model and allow users to follow setup links *outside* the app. Keys are manually pasted into the existing Android encrypted store. **Changing provider host clears the single existing cloud API key first**; the app fails closed if clearing fails. A later project should introduce a separate Keystore key per provider. Custom HTTPS providers remain available.

## Not yet supported as direct provider
- Google Cloud Speech-to-Text **V1**: 60 free minutes per month for eligible billed accounts, see https://cloud.google.com/speech-to-text/pricing and https://docs.cloud.google.com/speech-to-text/docs/setup . **Cloud billing must be enabled**; the quota is not a free public API key. Using OAuth tokens or a short-lived auth broker requires its own safe connector: no service-account JSON in a keyboard settings field.
- Android system voice input: available via inherited external input-method route without a separate developer API key. Google recognition availability and processing location depend on installed speech service.
- OpenAI **GPT-Live-Transcribe** and Mistral **Voxtral Realtime 3**: need a realtime WebSocket pipeline, NOT the file-upload endpoint. Documentation: https://developers.openai.com/api/docs/guides/transcription and https://docs.mistral.ai/models/voxtral-mini-transcribe-realtime-3-0 .

## Security and product acceptance
- No developer/owner-operated relay, account creation or account credentials stored by Tsuzuri.
- Do not describe API-key creation as OAuth. OpenAI/Mistral use API keys in this flow; Google Cloud is different.
- No silent audio upload: users select a cloud provider, add their own key, and invoke the microphone.
- Ordinary transcription and AI rewrite remain separate operations; cloud cleanup is not automatically enabled in this PR.
- Test switching OpenAI ↔ Mistral and clearing old key; verify manual custom HTTPS endpoint, recording upload, cancellation, API errors, and supported formats.
- Physical Android verification and integrated CI/review are required before release.

