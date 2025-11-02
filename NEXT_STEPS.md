# Next Steps: Dynamic gRPC Service Registry and Invocation

## Current State
We have successfully implemented:
- Dynamic proto file loading
- Descriptor set generation
- Dynamic message creation
- gRPC call execution

## Enhancement Plan

### 1. Service Registry Enhancement
The service registry should be expanded to include:

```json
{
  "serviceId": "user-service",
  "name": "UserService",
  "package": "com.example.users",
  "version": "1.0",
  "protoDefinition": "syntax = 'proto3'...",  // or URL/path to proto file
  "endpoints": [
    {
      "name": "getUser",
      "methodName": "GetUser",  // actual gRPC method name
      "inputMessage": "GetUserRequest",
      "outputMessage": "GetUserResponse",
      "inputMapping": {
        "id": "$.userId",
        "includeDetails": "$.full"
      },
      "outputMapping": {
        "user": "$.result",
        "status": "$.metadata.status"
      }
    }
  ],
  "instances": [
    {
      "host": "user-service",
      "port": 50051,
      "health": "UP",
      "metadata": {
        "zone": "us-east-1",
        "version": "1.0.0"
      }
    }
  ],
  "security": {
    "requiresAuth": true,
    "authType": "bearer"
  }
}
```

### 2. Dynamic Service Discovery
1. **Proto File Management**
   - Store proto files in Git/artifact repository
   - Support proto file versioning
   - Dynamic proto file loading and caching
   - Support for proto dependencies

2. **Service Registration**
   - REST endpoint for service registration
   - Validation of proto definitions
   - Health check integration
   - Service deregistration

3. **Method Discovery**
   - Extract available methods from proto
   - Map REST-like paths to gRPC methods
   - Support for method metadata

### 3. Request Processing Flow
1. Incoming REST/JSON request
2. Look up service in registry
3. Load proto definition
4. Map JSON request to proto message
5. Execute gRPC call
6. Transform response back to JSON
7. Return to client

### 4. Implementation Components

#### ServiceDefinition Enhancement
```java
public class ServiceDefinition {
    private String serviceId;
    private String name;
    private String packageName;
    private String version;
    private String protoDefinition;
    private List<EndpointDefinition> endpoints;
    private List<ServiceInstance> instances;
    private SecurityConfig security;
}

public class EndpointDefinition {
    private String name;
    private String methodName;
    private String inputMessage;
    private String outputMessage;
    private Map<String, String> inputMapping;
    private Map<String, String> outputMapping;

    // Getters
    public String getName() {
        return name;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getInputMessage() {
        return inputMessage;
    }

    public String getOutputMessage() {
        return outputMessage;
    }

    public Map<String, String> getInputMapping() {
        return inputMapping;
    }

    public Map<String, String> getOutputMapping() {
        return outputMapping;
    }

    // Setters
    public void setName(String name) {
        this.name = name;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public void setInputMessage(String inputMessage) {
        this.inputMessage = inputMessage;
    }

    public void setOutputMessage(String outputMessage) {
        this.outputMessage = outputMessage;
    }

    public void setInputMapping(Map<String, String> inputMapping) {
        this.inputMapping = inputMapping;
    }

    public void setOutputMapping(Map<String, String> outputMapping) {
        this.outputMapping = outputMapping;
    }
}

public class ServiceInstance {
    private String host;
    private int port;
    private String health;
    private Map<String, String> metadata;
}
```

#### Dynamic Method Invocation
```java
public class DynamicGrpcInvoker {
    public Future<JsonObject> invoke(
        ServiceDefinition service,
        String methodName,
        JsonObject request
    ) {
        // 1. Load proto definition
        // 2. Create dynamic message
        // 3. Apply input mapping
        // 4. Make gRPC call
        // 5. Apply output mapping
        // 6. Return JSON response
    }
}
```

### 5. Configuration Management
- Proto file storage location
- Service registry persistence
- Health check intervals
- Cache settings
- Security configurations

### 6. Monitoring and Observability
- Service health metrics
- Call latency tracking
- Error rate monitoring
- Circuit breaker integration
- Request/response logging

### 7. Security Considerations
- TLS for gRPC connections
- Authentication token forwarding
- Service-to-service authentication
- Rate limiting
- Access control

## Next Implementation Steps

1. Create `ServiceRegistry` enhancement
   - Add new fields to `ServiceDefinition`
   - Implement proto file management
   - Add endpoint mapping

2. Implement Dynamic Service Discovery
   - Create REST endpoints for registration
   - Implement proto file loading/caching
   - Add health check system

3. Create Request Processor
   - Implement JSON to Proto conversion
   - Add mapping engine
   - Create response transformer

4. Add Security Layer
   - Implement authentication
   - Add TLS support
   - Create access control

5. Setup Monitoring
   - Add metrics collection
   - Implement health checks
   - Create dashboard templates

## Benefits
- No hardcoded service definitions
- Dynamic service discovery
- Runtime proto loading
- Flexible mapping system
- Enhanced monitoring
- Improved security

## Challenges to Address
- Proto file versioning
- Schema evolution
- Performance optimization
- Cache invalidation
- Error handling
- Circuit breaking
- Rate limiting

## Practical Example: Dynamic Service Invocation

### Example Registry Entry
```json
{
  "serviceId": "order-service",
  "name": "OrderService",
  "package": "com.example.orders",
  "version": "1.0",
  "protoDefinition": "syntax = 'proto3';
    package com.example.orders;
    
    service OrderService {
      rpc CreateOrder (CreateOrderRequest) returns (OrderResponse) {}
      rpc GetOrder (GetOrderRequest) returns (OrderResponse) {}
    }
    
    message CreateOrderRequest {
      string customerId = 1;
      repeated OrderItem items = 2;
      string currency = 3;
    }
    
    message OrderItem {
      string productId = 1;
      int32 quantity = 2;
    }
    
    message GetOrderRequest {
      string orderId = 1;
    }
    
    message OrderResponse {
      string orderId = 1;
      string status = 2;
      double totalAmount = 3;
      repeated OrderItem items = 4;
    }",
  "endpoints": [
    {
      "name": "createOrder",
      "methodName": "CreateOrder",
      "inputMessage": "CreateOrderRequest",
      "outputMessage": "OrderResponse",
      "inputMapping": {
        "customerId": "$.customer.id",
        "items": "$.products[*]",
        "currency": "$.paymentInfo.currency"
      },
      "outputMapping": {
        "order": {
          "id": "$.orderId",
          "status": "$.status",
          "total": "$.totalAmount",
          "items": "$.items[*]"
        }
      }
    }
  ],
  "instances": [
    {
      "host": "order-service-prod",
      "port": 50051,
      "health": "UP",
      "metadata": {
        "zone": "us-east-1",
        "version": "1.0.0"
      }
    }
  ]
}
```

### Example Usage Flow

1. **Incoming REST Request**
```json
POST /api/orders
{
  "customer": {
    "id": "CUST123"
  },
  "products": [
    {
      "productId": "PROD789",
      "quantity": 2
    }
  ],
  "paymentInfo": {
    "currency": "USD"
  }
}
```

2. **Service Lookup**
```java
ServiceDefinition orderService = registry.getService("order-service");
EndpointDefinition createOrder = orderService.getEndpoint("createOrder");
```

3. **Dynamic Message Creation**
```java
// The system will automatically:
// 1. Load the proto definition
// 2. Create descriptor set
// 3. Build dynamic message based on mapping
DynamicMessage request = messageBuilder.createFrom(
    createOrder.getInputMessage(),
    incomingJson,
    createOrder.getInputMapping()
);
```

4. **Service Call**
```java
ServiceInstance instance = orderService.getActiveInstance();
Future<JsonObject> response = grpcInvoker.invoke(
    orderService,
    "CreateOrder",
    request
);
```

5. **Response Transformation**
```json
{
  "order": {
    "id": "ORD456",
    "status": "CREATED",
    "total": 59.98,
    "items": [
      {
        "productId": "PROD789",
        "quantity": 2
      }
    ]
  }
}
```

### Implementation Example

```java
public class DynamicServiceCaller {
    private final ServiceRegistry registry;
    private final DynamicGrpcInvoker grpcInvoker;
    private final MessageBuilder messageBuilder;

    public Future<JsonObject> callService(String serviceId, String endpoint, JsonObject request) {
        return vertx.executeBlocking(promise -> {
            try {
                // 1. Get service definition
                ServiceDefinition service = registry.getService(serviceId);
                if (service == null) {
                    promise.fail("Service not found: " + serviceId);
                    return;
                }

                // 2. Get endpoint definition
                EndpointDefinition endpointDef = service.getEndpoint(endpoint);
                if (endpointDef == null) {
                    promise.fail("Endpoint not found: " + endpoint);
                    return;
                }

                // 3. Create dynamic message
                DynamicMessage grpcRequest = messageBuilder.createFrom(
                    endpointDef.getInputMessage(),
                    request,
                    endpointDef.getInputMapping()
                );

                // 4. Make gRPC call
                grpcInvoker.invoke(service, endpointDef.getMethodName(), grpcRequest)
                    .onSuccess(response -> {
                        // 5. Transform response using output mapping
                        JsonObject mappedResponse = messageBuilder.toJson(
                            response,
                            endpointDef.getOutputMapping()
                        );
                        promise.complete(mappedResponse);
                    })
                    .onFailure(promise::fail);

            } catch (Exception e) {
                promise.fail(e);
            }
        });
    }
}
```

This example demonstrates:
- Complete flow from REST to gRPC and back
- Dynamic message creation from JSON
- Mapping configuration usage
- Error handling
- Instance selection
- Response transformation

The key advantage is that all service-specific information is stored in the registry, allowing the code to be completely generic and work with any gRPC service that follows the registration format

### 5. Handling Lists in Dynamic gRPC Mapping

#### Example Proto Definition with Lists
```protobuf
syntax = "proto3";
package inventory;

service InventoryService {
    rpc ProcessItems (ItemList) returns (ProcessResponse) {}
}

message Item {
    string id = 1;
    string name = 2;
    double price = 3;
    repeated string tags = 4;  // A list within an item
}

message ItemList {
    repeated Item items = 1;   // List of items
}

message ProcessResponse {
    bool success = 1;
    repeated string processedIds = 2;
    string message = 3;
}
```

#### JSON to Proto Mapping for Lists
Example JSON input:
```json
{
  "items": [
    {
      "id": "item1",
      "name": "Product A",
      "price": 29.99,
      "tags": ["electronics", "sale"]
    },
    {
      "id": "item2",
      "name": "Product B",
      "price": 49.99,
      "tags": ["appliance"]
    }
  ]
}
```

#### Dynamic Message Building with Lists
```java
public class DynamicMessageBuilder {
    public DynamicMessage buildMessageFromJson(Descriptors.Descriptor descriptor, JsonObject jsonInput) {
        DynamicMessage.Builder messageBuilder = DynamicMessage.newBuilder(descriptor);
        
        // Iterate through all fields in the descriptor
        for (Descriptors.FieldDescriptor field : descriptor.getFields()) {
            if (field.isRepeated()) {
                handleRepeatedField(messageBuilder, field, jsonInput);
            } else {
                handleSingleField(messageBuilder, field, jsonInput);
            }
        }
        
        return messageBuilder.build();
    }
    
    private void handleRepeatedField(DynamicMessage.Builder builder, 
                                   Descriptors.FieldDescriptor field, 
                                   JsonObject jsonInput) {
        JsonArray jsonArray = jsonInput.getJsonArray(field.getName());
        if (jsonArray == null) return;
        
        switch (field.getType()) {
            case MESSAGE:
                // Handle repeated message (complex objects)
                for (int i = 0; i < jsonArray.size(); i++) {
                    JsonObject elementJson = jsonArray.getJsonObject(i);
                    DynamicMessage nestedMessage = buildMessageFromJson(
                        field.getMessageType(), 
                        elementJson
                    );
                    builder.addRepeatedField(field, nestedMessage);
                }
                break;
                
            case STRING:
            case INT32:
            case DOUBLE:
                // Handle other primitive types
                for (Object element : jsonArray) {
                    builder.addRepeatedField(field, convertJsonValueToProtoValue(element, field));
                }
                break;
        }
    }
    
    private void handleSingleField(DynamicMessage.Builder builder, 
                                 Descriptors.FieldDescriptor field, 
                                 JsonObject jsonInput) {
        if (!jsonInput.containsKey(field.getName())) return;
        
        Object value = jsonInput.getValue(field.getName());
        if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
            JsonObject nestedJson = jsonInput.getJsonObject(field.getName());
            DynamicMessage nestedMessage = buildMessageFromJson(
                field.getMessageType(), 
                nestedJson
            );
            builder.setField(field, nestedMessage);
        } else {
            builder.setField(field, convertJsonValueToProtoValue(value, field));
        }
    }
    
    private Object convertJsonValueToProtoValue(Object value, Descriptors.FieldDescriptor field) {
        switch (field.getType()) {
            case INT32:
            case SINT32:
            case FIXED32:
                return ((Number) value).intValue();
            case INT64:
            case SINT64:
            case FIXED64:
                return ((Number) value).longValue();
            case DOUBLE:
                return ((Number) value).doubleValue();
            case FLOAT:
                return ((Number) value).floatValue();
            case BOOL:
                return (Boolean) value;
            case STRING:
                return String.valueOf(value);
            case ENUM:
                return field.getEnumType().findValueByName(String.valueOf(value));
            default:
                throw new IllegalArgumentException("Unsupported field type: " + field.getType());
        }
    }
}

// Usage example
DynamicMessageBuilder messageBuilder = new DynamicMessageBuilder();
DynamicMessage requestMessage = messageBuilder.buildMessageFromJson(itemListDescriptor, jsonInput);
```

This approach:
1. Uses descriptor information to dynamically handle fields
2. Supports nested messages automatically
3. Handles type conversion for all protobuf types
4. Processes repeated fields without hardcoding
5. Maintains type safety through proper conversion

#### Service Registry Configuration for Lists
```json
{
  "serviceId": "inventory-service",
  "name": "InventoryService",
  "package": "inventory",
  "endpoints": [
    {
      "name": "processItems",
      "methodName": "ProcessItems",
      "inputMessage": "ItemList",
      "outputMessage": "ProcessResponse",
      "inputMapping": {
        "$": "$.items",  // Map root array to items
        "$[*]": {       // Map each array element
          "*": "$.*"    // Dynamic field mapping
        }
      },
      "outputMapping": {
        "*": "$.*"      // Dynamic response mapping
      }
    }
  ]
}
```

The new mapping syntax:
- Uses `$` to represent the root element
- Uses `*` for dynamic field matching
- Uses `[*]` for array iterations
- Supports nested mappings with `{}`

### 6. Single Object Dynamic Mapping Example

#### Example Proto Definition with Single Object
```protobuf
syntax = "proto3";
package user;

service UserService {
    rpc UpdateUserProfile (UserProfile) returns (UpdateResponse) {}
}

message UserProfile {
    string id = 1;
    string name = 2;
    int32 age = 3;
    repeated string interests = 4;    // A list within the object
    Address address = 5;             // Nested message
}

message Address {
    string street = 1;
    string city = 2;
    string country = 3;
}

message UpdateResponse {
    bool success = 1;
    string message = 2;
    UserProfile updated_profile = 3;
}
```

#### JSON Input Example
```json
{
    "id": "user123",
    "name": "John Doe",
    "age": 30,
    "interests": ["reading", "travel"],
    "address": {
        "street": "123 Main St",
        "city": "Springfield",
        "country": "USA"
    }
}
```

#### Service Registry Configuration for Single Object
```json
{
  "serviceId": "user-service",
  "name": "UserService",
  "package": "user",
  "endpoints": [
    {
      "name": "updateProfile",
      "methodName": "UpdateUserProfile",
      "inputMessage": "UserProfile",
      "outputMessage": "UpdateResponse",
      "inputMapping": {
        "*": "$.*"    // Direct field mapping from root object
      },
      "outputMapping": {
        "profile": "$.updated_profile.*",
        "status": {
          "success": "$.success",
          "message": "$.message"
        }
      }
    }
  ]
}
```

In this case, since we're dealing with a single object:
- The `*: "$.*"` mapping indicates that all fields from the root JSON object map directly to the proto message fields
- No need for array iteration syntax `[*]` in the root level
- Nested objects (like `address`) are handled automatically
- Arrays within the object (like `interests`) are still handled as repeated fields

The same `DynamicMessageBuilder` handles both cases:
```java
// For single object mapping
JsonObject userProfileJson = new JsonObject()
    .put("id", "user123")
    .put("name", "John Doe")
    .put("age", 30);

DynamicMessage message = messageBuilder.buildMessageFromJson(userProfileDescriptor, userProfileJson);

// The builder detects it's a single object because:
// 1. The root descriptor is not a repeated field
// 2. The JSON input is an object, not an array
```

#### Key Differences from List Example
1. Root Level Mapping:
   - List example: `"$": "$.items"` (maps to repeated field)
   - Single object: `"*": "$.*"` (maps directly to message fields)

2. Field Access:
   - List example: Needs array iteration `$[*]`
   - Single object: Direct field access `$.*`

3. Type Handling:
   ```java
   private void handleSingleField(DynamicMessage.Builder builder, 
                                Descriptors.FieldDescriptor field, 
                                JsonObject jsonInput) {
       // Same code as before, but now handling root level fields directly
       if (!jsonInput.containsKey(field.getName())) return;
       
       Object value = jsonInput.getValue(field.getName());
       if (field.getType() == Descriptors.FieldDescriptor.Type.MESSAGE) {
           // Handle nested objects
           JsonObject nestedJson = jsonInput.getJsonObject(field.getName());
           DynamicMessage nestedMessage = buildMessageFromJson(
               field.getMessageType(), 
               nestedJson
           );
           builder.setField(field, nestedMessage);
       } else if (field.isRepeated()) {
           // Handle arrays within the object
           JsonArray array = jsonInput.getJsonArray(field.getName());
           handleRepeatedField(builder, field, array);
       } else {
           // Handle simple fields
           builder.setField(field, convertJsonValueToProtoValue(value, field));
       }
   }
   ```

This approach maintains the same dynamic handling logic but simplifies the mapping when dealing with single objects. The `DynamicMessageBuilder` automatically adapts its behavior based on the message descriptor and input JSON structure.
