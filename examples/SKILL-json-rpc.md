---
name: json-rpc
description: "Establish a JSON-RPC communication between a client and server where the client sends an object with method names and parameters to trigger a procedure or action defined by the server."
metadata:
  version: 1.0.0
  learned-date: 2026-06-22
  target-model: claude-opus-4-8
  cutoff: 2026-01
---
Establish a JSON-RPC communication between a client and server where the client sends an object with method names and parameters to trigger a procedure or action defined by the server.

## Overview
This skill is designed for generating JSON-RPC requests. It operates on data structures provided as input to simulate communication in applications that use this protocol, such as web services, mobile apps, and browsers.

## When to Use
- You need to interface with an application running over HTTP or similar protocols where RPC functionality is required.
- Your application implements the client part of JSON-RPC, sending requests to a remote server for actions like data manipulation, querying, etc., and expecting responses back.

## Instructions

### Generating a Request Object
To generate a JSON-RPC request that triggers an action on your behalf, you need to specify:
- The `jsonrpc` version (current practice often uses 2.0).
- The `method`, which is the procedure name available in the server.
- Optionally, you can provide a payload via `params`.

For Example of a valid JSON-RPC request,
```json
{
  "jsonrpc": "2.0",
  "method": "calculateSquare", // Server-side method to execute on receive
  "params": [9] // Payload being sent from client side, this example simply requests squaring operation where 9 is squared.
}
```

### Generating a Response Object

Responses are returned by the server and include:
- A `jsonrpc` version (matches what was used in the request).
- An optional parameter `id`. If specified it should match the `id` value from when the corresponding `request object` or method was called, allowing for validation.
- A response containing either an error (`error`) with a message description if something went wrong during execution of the procedure indicated by the `method`, or a return value expected if the remote service successfully carried out the action.

## Modern vs deprecated

As of the knowledge cutoff date, only two versions were recognized: 1.0 and 2.0.
- The **deprecated** version is 1.0 and was used before 2.0 for compatibility reasons with existing implementations.
- For ensuring forward-compatibility and adherence to industry standards, it's recommended to use the latest version (2.0).

### Validations:
- Ensure that all `method` values do not start with `"rpc."`.
- Check if a valid `id` is being returned in response objects for request-correlation purposes.

## Examples

Here are examples of how to generate both Request and Response JSON-RPC messages based on what sources have provided:

### Generating a Request Object (Example)
```json
{
  "jsonrpc": "2.0",
  "method": "calculateSquare", // Method name on Server side expecting this Request
  "params": [9] // Payload for the specified method
}
```

### Response from Server Example
When client sends above `request` and server executes successfully squaring number:

```json
{
  "error": null,
  "id": 0, 
  "result": 81  // This is typically provided as response payload after executing server-side operations.
}
```

## Sources

- [Source 1](https://en.wikipedia.org/wiki/JSON-RPC) - https://en.wikipedia.org/wiki/JSON-RPC
- [Source 2](https://www.jsonrpc.org/specification) - https://www.jsonrpc.org/specification
