import dev.bnorm.piecemeal.Piecemeal

@Piecemeal
class ConstructorPrivate <!PIECEMEAL_PRIVATE_CONSTRUCTOR!>private<!> constructor(s: String)

@Piecemeal
class ConstructorProtected protected constructor(s: String)

@Piecemeal
class ConstructorInternal internal constructor(s: String)

@Piecemeal
class ConstructorPublic public constructor(s: String)
