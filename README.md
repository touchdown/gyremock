# gyremock
grpc translator wrapper for wiremock in scala

## Overview
This is inspired by [grpc-wiremock](https://github.com/Adven27/grpc-wiremock) and example files are borrowed from there as well.

### Implementation
From proto files, it runs code gen to generate implementations for those protos via redirecting proto requests to predefined json endpoints and vice versa back to the client.

## Quick Usage
1) Run 
```posh
docker run -p 18080:18080 -p 50000:50000 -v $(pwd)/example/proto:/opt/gyremock/proto -v $(pwd)/example/wiremock:/opt/gyremock/wiremock touchdown/gyremock:<version>
```
Optionally
* one can add remote wiremock host like `-e WIREMOCK_HOST=host.docker.internal` for a locally port-forwarded connection
* one can add java opts like `-e JAVA_OPTS="-XX:+UseG1GC -Xms1G -Xmx4G -Xss2M"` for large amounts of proto files
* one can add extra jars like `-v $(pwd)/<lib>:/opt/gyremock/lib` if the proto files needed them

2) Stub 
```json
curl -X POST http://localhost:18080/__admin/mappings \
  -d '{
    "request": {
        "method": "POST",
        "url": "/BalanceService/getUserBalance",
        "bodyPatterns" : [ {
            "equalToJson" : { "id": "1", "currency": "EUR" }
        } ]
    },
    "response": {
        "status": 200,
        "jsonBody": { 
            "balance": { 
                "amount": { "value": { "decimal" : "100.0" }, "value_present": true },
                "currency": { "value": "EUR", "value_present": true }
            } 
        }
    }
}'
```

3) Check 
```posh
grpcurl -plaintext -d '{"id": 1, "currency": "EUR"}' localhost:50000 api.wallet.BalanceService/getUserBalance
```

Should get response:
```json
{
  "balance": {
    "amount": {
      "value": {
        "decimal": "100.0"
      },
      "value_present": true
    },
    "currency": {
      "value": "EUR",
      "value_present": true
    }
  }
}
```
## Stubbing

Stubbing should be done via [WireMock JSON API](http://wiremock.org/docs/stubbing/)
