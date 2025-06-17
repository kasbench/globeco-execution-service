# Supplemental Requirement 3

The purpose of this requirement is to support more efficient integration with the UI and to improve consistency with other microservices in the GlobeCo suite.

## Enhancement 1: Modify POST /api/v1/executions to accept multiple executions per invocation.
- Backward compatibility is NOT required. This API is not currently called by another other service.
- The POST should accept all executions that pass validation.
- The response object should include all the fields currently returned and be enhanced to indicate the success or failure of each item.  The HTTP status code and response should be indicative of whether the POST was fully successfully, partially successful, or completely failed.  Follow best practices. 
- Allow up to 100 executions per post.

# Enhancement 2: Modify GET /api/v1//executions to include filtering, sorting, and pagination
- Backward compatibility is NOT required. This API is not currently called by another other service.
- Use fields offset and limit for pagination for consistency with other services
- Replace securityId in the DTO with "security": {"securityId": "string", "ticker": "string"}.  Map securityId to ticker using the Security Service.  Instructions are in the [SECURITY_SERVICE_API_GUIDE.md](SECURITY_SERVICE_API_GUIDE.md)


## Integrations

| Service | Host | Port | OpenAPI Spec |
| --- | --- | --- | --- |
| Security Service | globeco-security-service | 8000 | [globeco-security-service-openapi.yaml](globeco-security-service-openapi.yaml)
| Portfolio Service | globeco-portfolio-service | 8001 | [lobeco-portfolio-service-openapi.yaml](globeco-portfolio-service-openapi.yaml)
---