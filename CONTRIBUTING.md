# Contributing to caravan-ledger

caravan-ledger is a demo of [events-caravan](https://github.com/SagynyshBaitursinov/events-caravan), maintained by
[Sagynysh Baitursinov](https://github.com/SagynyshBaitursinov). Issues and pull requests are welcome.

Proposals are evaluated against the events-caravan
[philosophy principles](https://github.com/SagynyshBaitursinov/events-caravan#philosophy-in-principles).

## Development setup

### Prerequisites

- **Java 25** (Temurin is what CI uses)
- **Docker** — the test environment emulates AWS locally (DynamoDB, SNS, SQS, Lambda) via
  [ministack](https://hub.docker.com/r/ministackorg/ministack) and Docker Compose
- **AWS CLI** — used by the local environment scripts to provision the emulated infrastructure

No AWS account is needed; everything runs locally against the emulator with dummy credentials.

### Build and test

```bash
# Full test suite, exactly as CI runs it:
# starts the local AWS environment, runs all tests, tears the environment down
./local/test

# Or, step by step:
./local/env-up          # start emulator, create tables/topics/queues/lambda
./local/execute-tests   # ./mvnw test, including integration tests
./local/env-down        # stop and clean up
```

## Pull request guidelines

1. **Open an issue first** for anything beyond a trivial fix, so the approach can be agreed on.
2. **Branch from `main`** and keep the PR focused on a single change.
3. **Add or update tests** for behavior changes. The `test` status check must pass before merging.
4. **Match the existing code style** of the module you are touching.

## Reporting security issues

Please do **not** open a public issue for security vulnerabilities — see [SECURITY.md](SECURITY.md).

## Code of conduct

This project follows the [Contributor Covenant Code of Conduct](CODE_OF_CONDUCT.md).

## License

By contributing, you agree that your contributions will be licensed under the
[Apache License 2.0](LICENSE).
