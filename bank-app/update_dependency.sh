#!/bin/bash
set -e

helm dependency update .
helm dependency update charts/accounts-service
helm dependency update charts/cash-service
helm dependency update charts/transfer-service
helm dependency update charts/exchange-service
helm dependency update charts/notifications-service